FROM node:22-alpine AS dependencies

WORKDIR /app
COPY package.json package-lock.json ./
# --ignore-scripts: скрипт prepare ставит git-хуки репозитория,
# а внутри образа нет ни .git, ни каталога scripts
RUN npm ci --prefer-offline --no-audit --fund=false --ignore-scripts

FROM node:22-alpine AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .

# Next.js «запекает» переменные NEXT_PUBLIC_* в бандл на этапе сборки,
# поэтому адрес API приходит аргументом сборки, а не переменной контейнера.
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

FROM node:22-alpine AS runtime

WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Режим standalone кладёт в .next/standalone собственный server.js и только те
# зависимости, которые реально нужны в рантайме, — образ выходит без node_modules
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

USER node
EXPOSE 3000
CMD ["node", "server.js"]
