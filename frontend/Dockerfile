FROM node:26-alpine@sha256:40cd011b55415bc894e45b02fdd5a5dfde056921b7d23a64c74f80497af48797 AS build

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
RUN apk upgrade --no-cache

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build --chown=101:101 /app/dist /usr/share/nginx/html

EXPOSE 8080

USER 101

CMD ["nginx", "-g", "daemon off;"]
