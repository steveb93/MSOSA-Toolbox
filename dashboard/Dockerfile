# UAF decision-intelligence dashboard image.
#
# Build context MUST be the repo root (not dashboard/) because we need to copy
# pyproject.toml and graph_mcp_driver/ — the editable install pulls SPARQL
# query constants and HTTP helpers from graph_mcp_driver.server.
#
#   docker build -f dashboard/Dockerfile -t uaf-dashboard .
#
# Or via compose (recommended):
#   docker compose -f docker-compose/docker-compose.yml \
#                  -f docker-compose/docker-compose.fuseki.yml \
#                  -f docker-compose/docker-compose.dashboard.yml up -d

FROM python:3.12-slim

WORKDIR /app

# Install runtime deps first so the layer caches across source changes.
COPY pyproject.toml ./
COPY dashboard/requirements.txt ./dashboard/requirements.txt
COPY graph_mcp_driver/__init__.py ./graph_mcp_driver/__init__.py
COPY graph_mcp_driver/__main__.py ./graph_mcp_driver/__main__.py
COPY graph_mcp_driver/server.py ./graph_mcp_driver/server.py
COPY README.md ./README.md

RUN pip install --no-cache-dir --upgrade pip \
 && pip install --no-cache-dir -e . \
 && pip install --no-cache-dir -r dashboard/requirements.txt

COPY dashboard/app.py ./dashboard/app.py

EXPOSE 8501

# --server.headless avoids the welcome-email prompt; --server.address 0.0.0.0
# lets the host reach the container port. Address NEO4J_SPARQL_RAW_URL etc.
# at runtime via compose env (see docker-compose.dashboard.yml).
CMD ["streamlit", "run", "dashboard/app.py", \
     "--server.headless", "true", \
     "--server.address", "0.0.0.0", \
     "--server.port", "8501"]
