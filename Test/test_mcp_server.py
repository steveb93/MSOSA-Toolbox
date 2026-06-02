"""
Unit tests for graph_mcp_driver.server.

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
    spec = importlib.util.find_spec("graph_mcp_driver.server")
    assert spec is not None, "graph_mcp_driver.server must be on the Python path"


# ---------------------------------------------------------------------------
# run_cypher behaviour

def test_run_cypher_returns_list_of_dicts():
    mock_record = MagicMock()
    mock_record.data.return_value = {"status": "connected"}
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([mock_record])

    with patch("graph_mcp_driver.server.driver", mock_driver):
        from graph_mcp_driver.server import run_cypher
        result = run_cypher("RETURN 'connected' AS status")

    assert isinstance(result, list)
    assert result == [{"status": "connected"}]


def test_run_cypher_empty_result():
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([])

    with patch("graph_mcp_driver.server.driver", mock_driver):
        from graph_mcp_driver.server import run_cypher
        result = run_cypher("MATCH (n) WHERE false RETURN n")

    assert result == []


def test_run_cypher_multiple_records():
    records = [
        MagicMock(**{"data.return_value": {"name": "Alice", "age": 30}}),
        MagicMock(**{"data.return_value": {"name": "Bob", "age": 25}}),
    ]
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session(records)

    with patch("graph_mcp_driver.server.driver", mock_driver):
        from graph_mcp_driver.server import run_cypher
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
    with patch("graph_mcp_driver.server.driver", mock_driver):
        from graph_mcp_driver.server import run_cypher
        run_cypher(cypher)

    mock_session.run.assert_called_once_with(cypher)


def test_run_cypher_uses_configured_database():
    """session() must be called with the configured database name."""
    mock_driver = MagicMock()
    mock_driver.session.return_value = _make_mock_session([])

    with patch("graph_mcp_driver.server.driver", mock_driver):
        import graph_mcp_driver.server as srv
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

    with patch("graph_mcp_driver.server.httpx.post", return_value=mock_response):
        from graph_mcp_driver.server import run_sparql
        result = run_sparql("SELECT ?cap ?name WHERE { ?cap a <Cap> }")

    assert result == [{"cap": "http://example/Cap1", "name": "Air Superiority"}]


def test_run_sparql_empty_bindings():
    mock_response = MagicMock()
    mock_response.json.return_value = {"results": {"bindings": []}}
    mock_response.raise_for_status = MagicMock()

    with patch("graph_mcp_driver.server.httpx.post", return_value=mock_response):
        from graph_mcp_driver.server import run_sparql
        result = run_sparql("SELECT ?x WHERE { ?x a <Nothing> }")

    assert result == []


def test_run_sparql_posts_query_with_auth_and_accept_header():
    mock_response = MagicMock()
    mock_response.json.return_value = {"results": {"bindings": []}}
    mock_response.raise_for_status = MagicMock()

    with patch("graph_mcp_driver.server.httpx.post", return_value=mock_response) as mock_post:
        from graph_mcp_driver.server import run_sparql
        run_sparql("ASK { ?s ?p ?o }")

    call = mock_post.call_args
    assert call.kwargs["data"] == {"query": "ASK { ?s ?p ?o }"}
    assert call.kwargs["headers"]["Accept"] == "application/sparql-results+json"
    assert call.kwargs["auth"] is not None


# ---------------------------------------------------------------------------
# Recommender tools (Stage 5)

def _mock_sparql_response(bindings):
    mock_response = MagicMock()
    mock_response.json.return_value = {"results": {"bindings": bindings}}
    mock_response.raise_for_status = MagicMock()
    return mock_response


def test_find_capability_gaps_returns_rows_ordered_by_pagerank():
    bindings = [
        {
            "capability": {"type": "uri", "value": "http://example/Cap1"},
            "name": {"type": "literal", "value": "Air Superiority"},
            "pagerank": {"type": "literal", "value": "0.85"},
        },
        {
            "capability": {"type": "uri", "value": "http://example/Cap2"},
            "name": {"type": "literal", "value": "Logistics"},
            "pagerank": {"type": "literal", "value": "0.42"},
        },
    ]
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response(bindings)):
        from graph_mcp_driver.server import find_capability_gaps
        result = find_capability_gaps()

    assert len(result) == 2
    assert result[0]["name"] == "Air Superiority"
    assert result[0]["pagerank"] == "0.85"


def test_find_capability_gaps_passes_limit_to_query():
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        from graph_mcp_driver.server import find_capability_gaps
        find_capability_gaps(limit=7)

    call = mock_post.call_args
    sent = call.kwargs["data"]["query"]
    assert "LIMIT 7" in sent
    assert "uafgds:pagerank" in sent
    assert "FILTER NOT EXISTS" in sent
    # Forward direction — the dump emits these directly so the query runs on
    # the non-reasoning endpoint without any OWL inverseOf inference.
    assert "uaf:realises|uaf:exhibits|uaf:tracesTo|uaf:implements" in sent
    assert "uaf:realisedBy" not in sent  # reasoner-derived; should not appear


def test_find_capability_gaps_routes_to_raw_endpoint():
    import graph_mcp_driver.server as srv
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        srv.find_capability_gaps()
    assert mock_post.call_args.args[0] == srv.sparql_raw_url


def test_recommend_resources_for_gap_returns_rows():
    bindings = [
        {
            "resource": {"type": "uri", "value": "http://example/Res1"},
            "name": {"type": "literal", "value": "Radar System"},
            "stereotype": {"type": "uri",
                           "value": "http://msosa-toolbox.local/uaf#ResourceArtifact"},
            "pagerank": {"type": "literal", "value": "0.71"},
            "peersRealised": {"type": "literal", "value": "5"},
        },
    ]
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response(bindings)):
        from graph_mcp_driver.server import recommend_resources_for_gap
        result = recommend_resources_for_gap("http://example/Cap1")

    assert result == [{
        "resource": "http://example/Res1",
        "name": "Radar System",
        "stereotype": "http://msosa-toolbox.local/uaf#ResourceArtifact",
        "pagerank": "0.71",
        "peersRealised": "5",
    }]


def test_recommend_resources_for_gap_interpolates_iri_and_k():
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        from graph_mcp_driver.server import recommend_resources_for_gap
        recommend_resources_for_gap("http://example/Cap42", k=3)

    sent = mock_post.call_args.kwargs["data"]["query"]
    assert "<http://example/Cap42>" in sent
    assert "LIMIT 3" in sent
    assert "COUNT(DISTINCT ?peer)" in sent
    assert "ORDER BY DESC(?peersRealised) DESC(?pagerank)" in sent
    # Forward-direction subject binding so the engine uses indexed predicate
    # lookups on directly-emitted triples (no reasoner needed).
    assert "?resource (uaf:realises|uaf:exhibits|uaf:tracesTo|uaf:implements) ?peer" in sent


def test_recommend_resources_for_gap_routes_to_raw_endpoint():
    import graph_mcp_driver.server as srv
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        srv.recommend_resources_for_gap("http://example/Cap1")
    assert mock_post.call_args.args[0] == srv.sparql_raw_url


def test_run_sparql_still_routes_to_reasoning_endpoint():
    """run_sparql is the public tool for arbitrary SPARQL; it must keep using
    the reasoning endpoint so semantic queries (subsumption, inverseOf-derived
    edges) still work. Only the recommender tools opted into the raw view."""
    import graph_mcp_driver.server as srv
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        srv.run_sparql("SELECT * WHERE { ?s ?p ?o } LIMIT 1")
    assert mock_post.call_args.args[0] == srv.sparql_url
    assert mock_post.call_args.args[0] != srv.sparql_raw_url


# ---------------------------------------------------------------------------
# Dashboard-feeding tools (Stage 5 decision dashboard)

def test_find_top_n_by_pagerank_returns_rows():
    bindings = [
        {
            "node": {"type": "uri", "value": "http://example/Node1"},
            "name": {"type": "literal", "value": "Alpha"},
            "type": {"type": "uri",
                     "value": "http://msosa-toolbox.local/uaf#Capability"},
            "pagerank": {"type": "literal", "value": "0.91"},
        },
    ]
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response(bindings)):
        from graph_mcp_driver.server import find_top_n_by_pagerank
        result = find_top_n_by_pagerank(limit=1)

    assert result == [{
        "node": "http://example/Node1",
        "name": "Alpha",
        "type": "http://msosa-toolbox.local/uaf#Capability",
        "pagerank": "0.91",
    }]


def test_find_top_n_by_pagerank_passes_limit_and_filters_uaf_types():
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        from graph_mcp_driver.server import find_top_n_by_pagerank
        find_top_n_by_pagerank(limit=11)
    sent = mock_post.call_args.kwargs["data"]["query"]
    assert "LIMIT 11" in sent
    assert "uafgds:pagerank" in sent
    assert "FILTER(STRSTARTS(STR(?type), STR(uaf:)))" in sent


def test_find_top_n_by_pagerank_routes_to_raw_endpoint():
    import graph_mcp_driver.server as srv
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        srv.find_top_n_by_pagerank()
    assert mock_post.call_args.args[0] == srv.sparql_raw_url


def test_count_nodes_by_domain_returns_rows():
    bindings = [
        {
            "domain": {"type": "literal", "value": "RESOURCE"},
            "count": {"type": "literal", "value": "412"},
        },
        {
            "domain": {"type": "literal", "value": "OPERATIONAL"},
            "count": {"type": "literal", "value": "287"},
        },
    ]
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response(bindings)):
        from graph_mcp_driver.server import count_nodes_by_domain
        result = count_nodes_by_domain()

    assert result == [
        {"domain": "RESOURCE", "count": "412"},
        {"domain": "OPERATIONAL", "count": "287"},
    ]


def test_count_nodes_by_domain_query_shape():
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        from graph_mcp_driver.server import count_nodes_by_domain
        count_nodes_by_domain()
    sent = mock_post.call_args.kwargs["data"]["query"]
    assert "GROUP BY ?domain" in sent
    assert "uaf:domain" in sent
    assert "COUNT(DISTINCT ?node)" in sent


def test_count_nodes_by_domain_routes_to_raw_endpoint():
    import graph_mcp_driver.server as srv
    with patch("graph_mcp_driver.server.httpx.post",
               return_value=_mock_sparql_response([])) as mock_post:
        srv.count_nodes_by_domain()
    assert mock_post.call_args.args[0] == srv.sparql_raw_url
