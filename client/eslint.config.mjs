import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import tseslint from "typescript-eslint";
import boundaries from "eslint-plugin-boundaries";
import checkFile from "eslint-plugin-check-file";
import importPlugin from "eslint-plugin-import";
import prettier from "eslint-config-prettier";

// Layer boundaries and file conventions are checked alongside Next.js rules.

const SLICED_LAYERS = ["views", "widgets", "features", "entities"];
const SEGMENTS = "ui|model|lib|api|config";

// Слайс лежит либо в <layer>/<slice>, либо в <layer>/<group>/<slice>.
// Сгруппированный вариант описан первым: у него второй уровень — не сегмент.
// Более глубокую вложенность отсекает scripts/check-fsd.mjs.
const slicedLayerElements = SLICED_LAYERS.flatMap((layer) => [
  {
    type: layer,
    pattern: `src/${layer}/*/!(${SEGMENTS})`,
    capture: ["group", "slice"],
  },
  { type: layer, pattern: `src/${layer}/*`, capture: ["slice"] },
]);

// Слой может импортировать только слои ниже себя.
const LOWER_LAYERS = {
  app: ["views", "widgets", "features", "entities"],
  views: ["widgets", "features", "entities"],
  widgets: ["features", "entities"],
  features: ["entities"],
  entities: [],
};

const layerPolicies = Object.entries(LOWER_LAYERS).map(([from, lower]) => ({
  from: { element: { type: from } },
  allow: [
    // Слайсы — только через публичный API. Импорт группы целиком невозможен:
    // у группы нет index.ts, и такой путь не резолвится.
    ...(lower.length > 0
      ? [{ to: { element: { types: lower, fileInternalPath: "index.ts" } } }]
      : []),
    // В shared глубокий импорт разрешён: слоёв и слайсов у него нет.
    { to: { element: { type: "shared" } } },
    // app может ссылаться на свои _providers и _styles.
    ...(from === "app" ? [{ to: { element: { type: "app" } } }] : []),
  ],
}));

const eslintConfig = defineConfig([
  ...nextVitals,
  ...tseslint.configs.recommended,
  // После typescript-eslint: next ослабляет часть его правил до warn.
  ...nextTs,
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { boundaries, "check-file": checkFile, import: importPlugin },
    settings: {
      "import/resolver": { typescript: { alwaysTryTypes: true } },
      // src/proxy.ts — файл фреймворка, слоем FSD не является.
      "boundaries/include": ["src/**/*"],
      "boundaries/ignore": ["src/proxy.ts"],
      "boundaries/elements": [
        { type: "app", pattern: "src/app" },
        { type: "shared", pattern: "src/shared" },
        ...slicedLayerElements,
      ],
    },
    rules: {
      // Направление импортов, изоляция слайсов одного слоя и публичный API.
      // Импорты внутри одного слайса правило не проверяет (checkInternals: false).
      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          message:
            "FSD: импорт {{from.element.type}} → {{to.element.type}} запрещён. Разрешены только нижние слои через index.ts слайса (shared — любой файл); слайсы одного слоя не импортируют друг друга.",
          policies: layerPolicies,
        },
      ],
      // Файл вне шести слоёв не принадлежит ни одному элементу.
      "boundaries/no-unknown-files": "error",

      "check-file/folder-naming-convention": [
        "error",
        {
          "src/*/": "@(app|views|widgets|features|entities|shared)",
          [`src/@(${SLICED_LAYERS.join("|")})/**/`]: "KEBAB_CASE",
        },
      ],
      "check-file/filename-naming-convention": [
        "error",
        {
          "src/!(app)/**/ui/**/!(index).{ts,tsx}": "PASCAL_CASE",
          "src/!(app)/**/@(model|lib|api|config)/*.{ts,tsx}": "CAMEL_CASE",
        },
        { ignoreMiddleExtensions: true },
      ],

      // Ловит и импорт группы целиком: у группы нет index.ts, путь не резолвится.
      "import/no-unresolved": "error",
      "import/no-cycle": "error",
      "max-lines": ["warn", { max: 120, skipBlankLines: false, skipComments: false }],
    },
  },
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // Отключает стилистические правила, конфликтующие с Prettier. Должен быть последним.
  prettier,
]);

export default eslintConfig;
