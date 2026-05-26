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

# Two npm pitfalls with the 2019-vintage upstream package.json:
#   1. Loose semver resolves `grunt: ^1.0.1` → `grunt@1.6.2` (needs Node 16+)
#      and `grunt-cli: ^1.3.2` → `grunt-cli@1.5.0` (needs Node 10+), so the
#      build would crash on Node 8. Pin both to the latest releases that
#      still support Node 8 before installing.
#   2. npm 6 refuses to run lifecycle scripts ("cannot run in wd") when the
#      process user differs from the package owner — which is always the
#      case inside a container running as root. `--unsafe-perm` bypasses
#      that guard.
#
# Install with `--ignore-scripts` so the upstream `postinstall: grunt release`
# does not fire with the wrong grunt (and so phantomjs/iltorb install
# scripts are skipped — neither is needed for the release build). Then
# invoke `grunt release` explicitly to produce /src/deploy.
RUN sed -i 's|"grunt": "\^1\.0\.1"|"grunt": "1.4.1"|' package.json \
 && sed -i 's|"grunt-cli": "\^1\.3\.2"|"grunt-cli": "1.3.2"|' package.json \
 && npm install --no-audit --no-fund --unsafe-perm --ignore-scripts \
 && ./node_modules/.bin/grunt release

# Serve the static build with nginx.
FROM nginx:alpine
COPY --from=build /src/deploy /usr/share/nginx/html
EXPOSE 80
