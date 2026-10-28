// Проверка структуры папок FSD, которую не видит ESLint (он смотрит только на файлы).
// Правила — в README.md, раздел «Архитектура». Запуск: npm run lint:fsd
import { readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SRC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../src");
const LAYERS = new Set(["app", "views", "widgets", "features", "entities", "shared"]);
const SLICED_LAYERS = ["views", "widgets", "features", "entities"];
const SEGMENTS = new Set(["ui", "model", "lib", "api", "config"]);
const PUBLIC_API = "index.ts";
// Next.js ищет proxy (бывший middleware) строго в src/ — слоем он не является.
const FRAMEWORK_FILES = new Set(["proxy.ts"]);

const errors = [];
const report = (dir, message) =>
  errors.push(`src/${path.relative(SRC, dir).split(path.sep).join("/")}: ${message}`);

function entries(dir) {
  const items = readdirSync(dir, { withFileTypes: true });
  return {
    dirs: items.filter((item) => item.isDirectory()).map((item) => item.name),
    files: items.filter((item) => item.isFile()).map((item) => item.name),
  };
}

// Слайс узнаётся по публичному API или по сегментам, иначе папка считается группой.
function isSlice(dir) {
  const { dirs, files } = entries(dir);
  return files.includes(PUBLIC_API) || dirs.some((name) => SEGMENTS.has(name));
}

function checkNoPublicApi(dir) {
  if (entries(dir).files.includes(PUBLIC_API)) {
    report(path.join(dir, PUBLIC_API), `${PUBLIC_API} допустим только на уровне слайса`);
  }
}

function checkSegment(segmentDir, segment) {
  checkNoPublicApi(segmentDir);
  for (const name of entries(segmentDir).dirs) {
    const nested = path.join(segmentDir, name);
    if (segment !== "ui" || name !== "common") {
      report(nested, `папка внутри сегмента запрещена (допустима только ui/common)`);
    } else if (entries(nested).dirs.length > 0) {
      report(nested, `папки внутри ui/common запрещены`);
    } else checkNoPublicApi(nested);
  }
}

function checkSlice(sliceDir) {
  const { dirs, files } = entries(sliceDir);
  if (!files.includes(PUBLIC_API)) report(sliceDir, `у слайса нет ${PUBLIC_API}`);
  for (const name of files.filter((file) => file !== PUBLIC_API)) {
    report(path.join(sliceDir, name), `файл вне сегмента (в корне слайса только ${PUBLIC_API})`);
  }
  for (const name of dirs) {
    const segmentDir = path.join(sliceDir, name);
    if (SEGMENTS.has(name)) checkSegment(segmentDir, name);
    else report(segmentDir, `недопустимый сегмент (разрешены: ${[...SEGMENTS].join(", ")})`);
  }
}

function checkGroup(groupDir) {
  const { dirs, files } = entries(groupDir);
  for (const name of files) {
    report(path.join(groupDir, name), `файл в группе запрещён: у группы нет public API`);
  }
  for (const name of dirs) {
    const childDir = path.join(groupDir, name);
    if (isSlice(childDir)) checkSlice(childDir);
    else if (entries(childDir).dirs.length > 0) {
      report(childDir, `вложенность глубже одной группы запрещена`);
    } else report(childDir, `у слайса нет ${PUBLIC_API}`);
  }
}

const top = entries(SRC);
for (const name of top.files.filter((file) => !FRAMEWORK_FILES.has(file))) {
  report(path.join(SRC, name), `файл вне слоёв`);
}
for (const name of top.dirs.filter((dir) => !LAYERS.has(dir))) {
  report(path.join(SRC, name), `недопустимый слой (разрешены: ${[...LAYERS].join(", ")})`);
}

for (const layer of SLICED_LAYERS.filter((name) => top.dirs.includes(name))) {
  const layerDir = path.join(SRC, layer);
  const { dirs, files } = entries(layerDir);
  for (const name of files) report(path.join(layerDir, name), `файл вне слайса`);
  for (const name of dirs) {
    const dir = path.join(layerDir, name);
    if (isSlice(dir)) checkSlice(dir);
    else checkGroup(dir);
  }
}

if (errors.length > 0) {
  console.error(`FSD: найдено нарушений — ${errors.length}\n`);
  for (const error of errors) console.error(`  ✖ ${error}`);
  process.exit(1);
}

console.log("FSD: структура src соответствует правилам");
