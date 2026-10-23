# RentCare web (SvelteKit adapter-node). Build context: web/
FROM node:24-bookworm-slim AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build && pnpm prune --prod

FROM node:24-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /app/package.json ./
COPY --from=build --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/build ./build
# Migration runs on start (idempotent, records applied versions).
COPY --from=build --chown=node:node /app/scripts/migrate.ts ./scripts/migrate.ts
COPY --from=build --chown=node:node /app/db ./db
COPY --from=build --chown=node:node /app/src/lib/server/db-ssl.ts ./src/lib/server/db-ssl.ts
USER node
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=6 \
  CMD node -e "fetch('http://127.0.0.1:3000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["sh", "-c", "node scripts/migrate.ts && exec node build"]
