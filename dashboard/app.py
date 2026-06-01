"""UAF decision-intelligence dashboard.

Streamlit app consuming the Stage 5 GDS write-back via the non-reasoning
`/uaf-raw/sparql` Fuseki endpoint. Targets the "Decision makers" persona
from `Ontology-Approach-to-Knowledge.md` §8.

Run (in the same venv as the MCP server / graph_mcp_driver):

    pip install -r dashboard/requirements.txt
    streamlit run dashboard/app.py

Prereqs in Neo4j + Fuseki:

  1. UAF export loaded and `init_uaf_graph.cypher` seeded.
  2. GDS write-back run: cookbook §6a (`gdsPagerank` on every projected node).
  3. `python ontology/codegen/dump_to_rdf.py` re-run after the write-back.
  4. Fuseki reloaded (restart container, or PUT to /uaf/data).

The four panels query the same SPARQL constants that the MCP tools use —
imported directly from `graph_mcp_driver.server` so query shape stays in
lockstep with what the LLM-side tools see.
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from graph_mcp_driver.server import (
    count_nodes_by_domain,
    find_capability_gaps,
    find_top_n_by_pagerank,
    recommend_resources_for_gap,
    sparql_raw_url,
)


st.set_page_config(page_title="UAF Decision Dashboard",
                   page_icon=None, layout="wide")


@st.cache_data(ttl=300, show_spinner=False)
def _gaps(limit: int) -> pd.DataFrame:
    return pd.DataFrame(find_capability_gaps(limit=limit))


@st.cache_data(ttl=300, show_spinner=False)
def _top_n(limit: int) -> pd.DataFrame:
    return pd.DataFrame(find_top_n_by_pagerank(limit=limit))


@st.cache_data(ttl=300, show_spinner=False)
def _recommendations(capability_iri: str, k: int) -> pd.DataFrame:
    return pd.DataFrame(recommend_resources_for_gap(capability_iri, k=k))


@st.cache_data(ttl=300, show_spinner=False)
def _domain_counts() -> pd.DataFrame:
    return pd.DataFrame(count_nodes_by_domain())


def _coerce_numeric(df: pd.DataFrame, cols: list[str]) -> pd.DataFrame:
    for c in cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df


st.title("UAF decision dashboard")
st.caption(f"Querying `{sparql_raw_url}` — refresh page after a new dump to clear cache")

with st.sidebar:
    st.header("Controls")
    gap_limit = st.slider("Gap rows", min_value=5, max_value=100, value=25, step=5)
    top_n_limit = st.slider("Top-N rows", min_value=5, max_value=100, value=25, step=5)
    rec_k = st.slider("Recommendations per gap", min_value=3, max_value=25, value=10, step=1)
    if st.button("Clear cache and reload"):
        st.cache_data.clear()
        st.rerun()

tab_gaps, tab_top, tab_recs, tab_domains = st.tabs(
    ["Coverage gaps", "Top-N influence", "Gap recommendations", "Domain composition"]
)


with tab_gaps:
    st.subheader("Capability coverage gaps")
    st.caption(
        "Capabilities with no realisation chain to a RESOURCE-domain element, "
        "ranked by GDS PageRank. Higher score = more consequential gap."
    )
    df = _coerce_numeric(_gaps(gap_limit), ["pagerank"])
    if df.empty:
        st.info("No gaps under §10 — every Capability has at least one resource realisation.")
    else:
        st.dataframe(df, use_container_width=True, hide_index=True)
        st.caption(f"{len(df)} gap(s) returned. Copy a `capability` IRI into the Gap Recommendations tab.")


with tab_top:
    st.subheader("Top-N most-influential elements")
    st.caption(
        "GDS PageRank over the full UAF graph. Surfaces load-bearing "
        "Capabilities, Activities, and System blocks — what the trace web "
        "converges on."
    )
    df = _coerce_numeric(_top_n(top_n_limit), ["pagerank"])
    if df.empty:
        st.info("No PageRank materialised yet. Run cookbook §6a, then dump_to_rdf.py, then reload Fuseki.")
    else:
        st.dataframe(df, use_container_width=True, hide_index=True)


with tab_recs:
    st.subheader("Recommend RESOURCE-domain candidates for a gap")
    st.caption(
        "Content-based: candidates are ranked by how many *other* Capabilities "
        "each already realises (peer-realiser frequency), tie-broken by "
        "PageRank. Universal players surface first."
    )
    capability_iri = st.text_input(
        "Capability IRI",
        placeholder="http://msosa-toolbox.local/uaf/instance#<id>",
        help="Copy a value from the `capability` column of the Coverage gaps tab.",
    )
    if capability_iri.strip():
        df = _coerce_numeric(
            _recommendations(capability_iri.strip(), rec_k),
            ["pagerank", "peersRealised"],
        )
        if df.empty:
            st.warning(
                "No candidates returned. The model may have no ResourceArtifacts "
                "realising multiple Capabilities, or the IRI doesn't match any "
                "RESOURCE-domain element."
            )
        else:
            st.dataframe(df, use_container_width=True, hide_index=True)
    else:
        st.info("Paste a Capability IRI above to see ranked candidate realisers.")


with tab_domains:
    st.subheader("Nodes per UAF domain")
    st.caption(
        "Distinct-node count grouped by `uaf:domain`. Useful for spotting "
        "under-modelled domains relative to programme priorities."
    )
    df = _coerce_numeric(_domain_counts(), ["count"])
    if df.empty:
        st.info("No nodes carry a uaf:domain literal in the current dump.")
    else:
        chart_df = df.set_index("domain")["count"]
        st.bar_chart(chart_df)
        st.dataframe(df, use_container_width=True, hide_index=True)
