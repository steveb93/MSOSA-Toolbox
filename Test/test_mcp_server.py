"""
Unit tests for neo4j_mcp_driver.server.

These tests mock the Neo4j driver so no live database is required.
"""

import importlib
from unittest.mock import MagicMock, patch

import pytest


def _make_mock_session(records):
    """Return a mock context-manager session whose .run() yields records."""
    session = MagicMock()
    session.run.return_value = records
    session.__enter__ = MagicMock(return_value=session)
    session.__exit__ = MagicMock(return_value=False)
    return session


# ---------------------------------------------------------------------------
# Importability

def test_server_module_is_importable():
    """Server must be importable; driver creation is lazy and never connects here."""
    spec = importlib.util.find_spec("neo4j_mcp_driver.server")
    assert spec is not None, "neo4j_mcp_driver.server must be on the Python path"


# ---------------------------------------------------------------------------
# run_cypher behaviour

def test_run_cypher_returns_list_of_dicts():
    mock_record = MagicMock()
    mock_record.data.return_value = {"status": "connected"}
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([mock_record])

    with patch("neo4j_mcp_driver.server.driver", mock_driver):
        from neo4j_mcp_driver.server import run_cypher
        result = run_cypher("RETURN 'connected' AS status")

    assert isinstance(result, list)
    assert result == [{"status": "connected"}]


def test_run_cypher_empty_result():
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([])

    with patch("neo4j_mcp_driver.server.driver", mock_driver):
        from neo4j_mcp_driver.server import run_cypher
        result = run_cypher("MATCH (n) WHERE false RETURN n")

    assert result == []


def test_run_cypher_multiple_records():
    records = [
        MagicMock(**{"data.return_value": {"name": "Alice", "age": 30}}),
        MagicMock(**{"data.return_value": {"name": "Bob", "age": 25}}),
    ]
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session(records)

    with patch("neo4j_mcp_driver.server.driver", mock_driver):
        from neo4j_mcp_driver.server import run_cypher
        result = run_cypher("MATCH (n:Person) RETURN n.name AS name, n.age AS age")

    assert len(result) == 2
    assert result[0] == {"name": "Alice", "age": 30}
    assert result[1] == {"name": "Bob", "age": 25}


def test_run_cypher_passes_query_to_session():
    """The query string must be forwarded to session.run unchanged."""
    mock_session = _make_mock_session([])
    mock_driver = MagicMock()
    mock_driver.session.return_value = mock_session

    cypher = "MATCH (n:Capability) RETURN n.name LIMIT 10"
    with patch("neo4j_mcp_driver.server.driver", mock_driver):
        from neo4j_mcp_driver.server import run_cypher
        run_cypher(cypher)

    mock_session.run.assert_called_once_with(cypher)


def test_run_cypher_uses_configured_database():
    """session() must be called with the configured database name."""
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([])

    with patch("neo4j_mcp_driver.server.driver", mock_driver):
        import neo4j_mcp_driver.server as srv
        srv.run_cypher("RETURN 1")
        mock_driver.session.assert_called_with(database=srv.database)


# ---------------------------------------------------------------------------
# run_sparql behaviour (mocked httpx — no live endpoint required)

def test_run_sparql_returns_list_of_dicts():
    mock_response = MagicMock()
    mock_response.json.return_value = {
        "results": {
            "bindings": [
                {
                    "cap": {"type": "uri", "value": "http://example/Cap1"},
                    "name": {"type": "literal", "value": "Air Superiority"},
                },
            ]
        }
    }
    mock_response.raise_for_status = MagicMock()

    with patch("neo4j_mcp_driver.server.httpx.post", return_value=mock_response):
        from neo4j_mcp_driver.server import run_sparql
        result = run_sparql("SELECT ?cap ?name WHERE { ?cap a <Cap> }")

    assert result == [{"cap": "http://example/Cap1", "name": "Air Superiority"}]


def test_run_sparql_empty_bindings():
    mock_response = MagicMock()
    mock_response.json.return_value = {"results": {"bindings": []}}
    mock_response.raise_for_status = MagicMock()

    with patch("neo4j_mcp_driver.server.httpx.post", return_value=mock_response):
        from neo4j_mcp_driver.server import run_sparql
        result = run_sparql("SELECT ?x WHERE { ?x a <Nothing> }")

    assert result == []


def test_run_sparql_posts_query_with_auth_and_accept_header():
    mock_response = MagicMock()
    mock_response.json.return_value = {"results": {"bindings": []}}
    mock_response.raise_for_status = MagicMock()

    with patch("neo4j_mcp_driver.server.httpx.post", return_value=mock_response) as mock_post:
        from neo4j_mcp_driver.server import run_sparql
        run_sparql("ASK { ?s ?p ?o }")

    call = mock_post.call_args
    assert call.kwargs["data"] == {"query": "ASK { ?s ?p ?o }"}
    assert call.kwargs["headers"]["Accept"] == "application/sparql-results+json"
    assert call.kwargs["auth"] is not None
