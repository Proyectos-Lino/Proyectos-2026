# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
USER root
RUN sed -i 's/^worker_processes .*;/worker_processes 1;/' /etc/nginx/nginx.conf
USER 101

ENV BACKEND_ORIGIN=http://califica-api:8080 \
    NGINX_ENVSUBST_FILTER="^BACKEND_ORIGIN$"
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=45s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
