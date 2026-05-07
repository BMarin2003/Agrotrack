FROM oven/bun:1.3.5-slim AS builder
WORKDIR /app
COPY package.json bun.lock* ./
RUN bun install --frozen-lockfile
COPY . .
RUN bun run build

FROM oven/bun:1.3.5-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/build/main.js ./main.js
EXPOSE 3000
CMD ["bun", "main.js"]
