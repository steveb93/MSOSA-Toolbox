"""UAF decision-intelligence dashboard.

Streamlit app consuming the Stage 5 GDS write-back via the non-reasoning
`/uaf-raw/sparql` Fuseki endpoint. Targets the "Decision makers" persona
from `Ontology-Approach-to-Knowledge.md` §8.

Two run modes:

  Standalone (in the same venv as graph_mcp_driver):

    pip install -r dashboard/requirements.txt
    streamlit run dashboard/app.py

  Docker (overlay alongside Neo4j + Fuseki):

    docker compose -f docker-compose/docker-compose.yml \
                   -f docker-compose/docker-compose.fuseki.yml \
                   -f docker-compose/docker-compose.dashboard.yml up -d

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

from datetime import datetime

import httpx
import pandas as pd
import streamlit as st

from graph_mcp_driver.server import (
    count_nodes_by_domain,
    find_capability_gaps,
    find_top_n_by_pagerank,
    recommend_resources_for_gap,
    sparql_auth,
    sparql_raw_url,
)


st.set_page_config(page_title="UAF Decision Dashboard",
                   page_icon=None, layout="wide")


# ─── Cached data fetchers ────────────────────────────────────────────────────

@st.cache_data(ttl=300, show_spinner="Querying Fuseki…")
def _gaps(limit: int) -> pd.DataFrame:
    return pd.DataFrame(find_capability_gaps(limit=limit))


@st.cache_data(ttl=300, show_spinner="Querying Fuseki…")
def _top_n(limit: int) -> pd.DataFrame:
    return pd.DataFrame(find_top_n_by_pagerank(limit=limit))


@st.cache_data(ttl=300, show_spinner="Querying Fuseki…")
def _recommendations(capability_iri: str, k: int) -> pd.DataFrame:
    return pd.DataFrame(recommend_resources_for_gap(capability_iri, k=k))


@st.cache_data(ttl=300, show_spinner="Querying Fuseki…")
def _domain_counts() -> pd.DataFrame:
    return pd.DataFrame(count_nodes_by_domain())


@st.cache_data(ttl=30, show_spinner=False)
def _health() -> dict:
    """Quick ASK probe — 5s timeout. Cached for 30s so we don't hammer Fuseki."""
    try:
        r = httpx.post(
            sparql_raw_url,
            auth=sparql_auth,
            data={"query": "ASK {}"},
            headers={"Accept": "application/sparql-results+json"},
            timeout=5.0,
        )
        r.raise_for_status()
        return {"ok": True, "checked_at": datetime.now()}
    except Exception as exc:
        return {"ok": False, "error": str(exc), "checked_at": datetime.now()}


def _coerce_numeric(df: pd.DataFrame, cols: list[str]) -> pd.DataFrame:
    for c in cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df


def _csv_download(df: pd.DataFrame, label: str, file_stem: str, key: str) -> None:
    st.download_button(
        label=label,
        data=df.to_csv(index=False).encode("utf-8"),
        file_name=f"{file_stem}-{datetime.now():%Y%m%d-%H%M%S}.csv",
        mime="text/csv",
        key=key,
    )


# ─── Header + endpoint health ────────────────────────────────────────────────

st.title("UAF decision dashboard")

health = _health()
status_emoji = "🟢" if health["ok"] else "🔴"
status_text = (
    f"{status_emoji} `{sparql_raw_url}` — "
    f"checked {health['checked_at']:%H:%M:%S}"
)
if not health["ok"]:
    status_text += f" — **{health['error']}**"
st.caption(status_text)
if not health["ok"]:
    st.error(
        "Fuseki is not responding. The JVM may be wedged after a heavy "
        "reasoning query — try `docker compose ... restart fuseki` and "
        "click **Clear cache and reload** below."
    )

# ─── Sidebar controls ────────────────────────────────────────────────────────

with st.sidebar:
    st.header("Controls")
    gap_limit = st.slider("Gap rows", min_value=5, max_value=100, value=25, step=5)
    top_n_limit = st.slider("Top-N rows", min_value=5, max_value=100, value=25, step=5)
    rec_k = st.slider("Recommendations per gap", min_value=3, max_value=25, value=10, step=1)
    if st.button("Clear cache and reload", use_container_width=True):
        st.cache_data.clear()
        st.session_state.pop("selected_gap_iri", None)
        st.rerun()
    st.divider()
    st.caption(
        "Refresh recipe after a new MSOSA export:\n\n"
        "1. cookbook §6a write-back\n"
        "2. `python ontology/codegen/dump_to_rdf.py`\n"
        "3. restart Fuseki container\n"
        "4. **Clear cache and reload** above"
    )

# ─── KPI metric row ──────────────────────────────────────────────────────────

domain_df = _coerce_numeric(_domain_counts(), ["count"]) if health["ok"] else pd.DataFrame()
gaps_df = _coerce_numeric(_gaps(gap_limit), ["pagerank"]) if health["ok"] else pd.DataFrame()
top_df = _coerce_numeric(_top_n(top_n_limit), ["pagerank"]) if health["ok"] else pd.DataFrame()

kpi1, kpi2, kpi3, kpi4 = st.columns(4)
kpi1.metric(
    "Stereotype-bearing nodes",
    f"{int(domain_df['count'].sum()):,}" if not domain_df.empty else "—",
    help="Sum of distinct nodes carrying a uaf:domain literal.",
)
kpi2.metric(
    "UAF domains modelled",
    len(domain_df) if not domain_df.empty else "—",
    help="Distinct uaf:domain values found in the dump.",
)
kpi3.metric(
    f"Coverage gaps (top {gap_limit})",
    len(gaps_df) if not gaps_df.empty else 0,
    help=("Capabilities with no realisation chain to a RESOURCE-domain "
          "element, up to the slider limit."),
)
kpi4.metric(
    "Max PageRank",
    f"{top_df['pagerank'].max():.3f}" if not top_df.empty else "—",
    help="Top score in the Top-N influence table — the most-depended-on element.",
)

st.divider()

# ─── Tabs ────────────────────────────────────────────────────────────────────

tab_gaps, tab_top, tab_recs, tab_domains = st.tabs(
    ["Coverage gaps", "Top-N influence", "Gap recommendations", "Domain composition"]
)


with tab_gaps:
    st.subheader("Capability coverage gaps")
    st.caption(
        "Capabilities with no realisation chain to a RESOURCE-domain element, "
        "ranked by GDS PageRank. **Select a row** to populate the Gap "
        "Recommendations tab automatically."
    )
    if gaps_df.empty:
        st.info("No gaps under §10 — every Capability has at least one resource realisation.")
    else:
        max_pr = float(gaps_df["pagerank"].max() or 1.0)
        event = st.dataframe(
            gaps_df,
            use_container_width=True,
            hide_index=True,
            on_select="rerun",
            selection_mode="single-row",
            column_config={
                "capability": st.column_config.TextColumn("Capability IRI"),
                "name": st.column_config.TextColumn("Name"),
                "pagerank": st.column_config.ProgressColumn(
                    "PageRank",
                    format="%.3f",
                    min_value=0.0,
                    max_value=max_pr,
                ),
            },
        )
        if event.selection.rows:
            idx = event.selection.rows[0]
            st.session_state["selected_gap_iri"] = gaps_df.iloc[idx]["capability"]
            st.success(
                f"Selected gap loaded into **Gap recommendations** tab "
                f"(row {idx + 1})."
            )
        st.caption(f"{len(gaps_df)} gap(s) returned.")
        _csv_download(gaps_df, "Download gaps CSV", "uaf-gaps", key="gaps-csv")


with tab_top:
    st.subheader("Top-N most-influential elements")
    st.caption(
        "GDS PageRank over the full UAF graph. Surfaces load-bearing "
        "Capabilities, Activities, and System blocks — what the trace web "
        "converges on."
    )
    if top_df.empty:
        st.info(
            "No PageRank materialised yet. Run cookbook §6a, then "
            "`dump_to_rdf.py`, then reload Fuseki."
        )
    else:
        max_pr = float(top_df["pagerank"].max() or 1.0)
        st.dataframe(
            top_df,
            use_container_width=True,
            hide_index=True,
            column_config={
                "node": st.column_config.TextColumn("Node IRI"),
                "name": st.column_config.TextColumn("Name"),
                "type": st.column_config.TextColumn("Type"),
                "pagerank": st.column_config.ProgressColumn(
                    "PageRank",
                    format="%.3f",
                    min_value=0.0,
                    max_value=max_pr,
                ),
            },
        )
        # Distribution chart — sorted descending, visualises the long-tail shape.
        dist = top_df[["pagerank"]].reset_index(drop=True)
        dist.index = dist.index + 1
        dist.index.name = "rank"
        st.caption("PageRank distribution (rank → score)")
        st.bar_chart(dist, height=200)
        _csv_download(top_df, "Download top-N CSV", "uaf-top-n", key="top-csv")


with tab_recs:
    st.subheader("Recommend RESOURCE-domain candidates for a gap")
    st.caption(
        "Content-based: candidates are ranked by how many *other* Capabilities "
        "each already realises (peer-realiser frequency), tie-broken by "
        "PageRank. Universal players surface first."
    )
    default_iri = st.session_state.get("selected_gap_iri", "")
    capability_iri = st.text_input(
        "Capability IRI",
        value=default_iri,
        placeholder="http://msosa-toolbox.local/uaf/instance#<id>",
        help=("Auto-filled when you select a row in the Coverage gaps tab. "
              "You can also paste any Capability IRI manually."),
    )
    if capability_iri.strip():
        rec_df = _coerce_numeric(
            _recommendations(capability_iri.strip(), rec_k),
            ["pagerank", "peersRealised"],
        )
        if rec_df.empty:
            st.warning(
                "No candidates returned. The model may have no ResourceArtifacts "
                "realising multiple Capabilities, or the IRI doesn't match any "
                "RESOURCE-domain element."
            )
        else:
            max_pr = float(rec_df["pagerank"].max() or 1.0)
            max_peers = int(rec_df["peersRealised"].max() or 1)
            st.dataframe(
                rec_df,
                use_container_width=True,
                hide_index=True,
                column_config={
                    "resource": st.column_config.TextColumn("Resource IRI"),
                    "name": st.column_config.TextColumn("Name"),
                    "stereotype": st.column_config.TextColumn("Stereotype"),
                    "pagerank": st.column_config.ProgressColumn(
                        "PageRank",
                        format="%.3f",
                        min_value=0.0,
                        max_value=max_pr,
                    ),
                    "peersRealised": st.column_config.ProgressColumn(
                        "Peers realised",
                        format="%d",
                        min_value=0,
                        max_value=max_peers,
                    ),
                },
            )
            _csv_download(rec_df, "Download recommendations CSV",
                          "uaf-recommendations", key="rec-csv")
    else:
        st.info(
            "Select a row in the **Coverage gaps** tab, or paste a Capability "
            "IRI above, to see ranked candidate realisers."
        )


with tab_domains:
    st.subheader("Nodes per UAF domain")
    st.caption(
        "Distinct-node count grouped by `uaf:domain`. Useful for spotting "
        "under-modelled domains relative to programme priorities."
    )
    if domain_df.empty:
        st.info("No nodes carry a uaf:domain literal in the current dump.")
    else:
        chart_df = domain_df.set_index("domain")["count"]
        st.bar_chart(chart_df, height=320)
        max_count = int(domain_df["count"].max() or 1)
        st.dataframe(
            domain_df,
            use_container_width=True,
            hide_index=True,
            column_config={
                "domain": st.column_config.TextColumn("Domain"),
                "count": st.column_config.ProgressColumn(
                    "Node count",
                    format="%d",
                    min_value=0,
                    max_value=max_count,
                ),
            },
        )
        _csv_download(domain_df, "Download domain counts CSV",
                      "uaf-domain-counts", key="dom-csv")
