FROM node:22-alpine@sha256:0e1754758c2df4cfa3e68d59e2e1b7374bb7fd9ea6328eaaacf15cc934af8202 AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

ARG VITE_GOOGLE_CLIENT_ID
ARG REQUIRE_GOOGLE_CLIENT_ID=false
ENV VITE_GOOGLE_CLIENT_ID=${VITE_GOOGLE_CLIENT_ID}

RUN if [ -z "$VITE_GOOGLE_CLIENT_ID" ]; then \
      if [ "$REQUIRE_GOOGLE_CLIENT_ID" = "true" ]; then \
        echo "ERROR: VITE_GOOGLE_CLIENT_ID is required to build the production frontend image." >&2; \
        exit 1; \
      else \
        echo "WARNING: VITE_GOOGLE_CLIENT_ID is not set. Google Sign-In will be disabled at runtime." >&2; \
      fi; \
    fi; \
    npm run build


FROM nginxinc/nginx-unprivileged:alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e

USER root
RUN apk upgrade --no-cache && apk add --no-cache 'libexpat>=2.10.0-r0' 'libxslt>=1.1.43-r4' 'musl>=1.2.6-r3' 'zlib>=1.3.2-r1'

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build --chown=101:101 /app/dist /usr/share/nginx/html

EXPOSE 8080

USER 101

CMD ["nginx", "-g", "daemon off;"]
