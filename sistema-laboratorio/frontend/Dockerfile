FROM node:24-alpine AS build
WORKDIR /build
ENV NODE_OPTIONS=--max-old-space-size=512

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY index.html tsconfig*.json vite.config.ts ./
COPY src ./src
COPY public ./public

ARG VITE_API_URL
RUN if [ -z "$VITE_API_URL" ]; then \
      echo "Define VITE_API_URL en los argumentos de compilacion de Dokploy." >&2; \
      exit 1; \
    fi; \
    VITE_API_URL="$VITE_API_URL" npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/dist /usr/share/nginx/html
RUN nginx -t

EXPOSE 8080
HEALTHCHECK --interval=60s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
