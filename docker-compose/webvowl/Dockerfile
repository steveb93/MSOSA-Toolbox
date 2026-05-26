# Build WebVOWL from source.
#
# Upstream ships a Dockerfile (https://github.com/VisualDataWeb/WebVOWL/blob/master/Dockerfile)
# that downloads a pre-built WAR from `vowl.visualdataweb.org`. That host
# now redirects to a third-party domain that returns 403, so the upstream
# image cannot be built. The README's caution about the visualdataweb.org
# domain handover is the same root cause.
#
# We rebuild from source instead. Upstream's .travis.yml only tested Node
# 4–6 (2015–2017 vintage) and the package.json pins matching deps (Webpack
# 1.x, phantomjs-prebuilt). `node:8-alpine` is the oldest still-available
# image on Docker Hub that runs cleanly with that dep tree.

FROM node:8-alpine AS build

ARG WEBVOWL_REF=master

# git for the source pull; python2 + make + g++ because a few 2015-vintage
# transitive deps still compile native bindings via node-gyp on install.
RUN apk add --no-cache git python make g++

# Skip the phantomjs-prebuilt binary download (used only by karma tests,
# not the release build) — its CDN was decommissioned in 2018.
ENV PHANTOMJS_SKIP_DOWNLOAD=true

WORKDIR /src
RUN git clone https://github.com/VisualDataWeb/WebVOWL.git . \
 && git checkout ${WEBVOWL_REF}

# package.json declares `"postinstall": "grunt release"` — `npm install`
# both fetches deps AND runs the grunt build, producing /src/deploy.
RUN npm install --no-audit --no-fund

# Serve the static build with nginx.
FROM nginx:alpine
COPY --from=build /src/deploy /usr/share/nginx/html
EXPOSE 80
