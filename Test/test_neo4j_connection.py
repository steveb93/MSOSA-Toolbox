import os
from neo4j import GraphDatabase

uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
user = os.getenv("NEO4J_USERNAME", "neo4j")
password = os.getenv("NEO4J_PASSWORD", "Password123")
database = os.getenv("NEO4J_DATABASE", "neo4j")

driver = GraphDatabase.driver(uri, auth=(user, password))
driver.verify_connectivity()

with driver.session(database=database) as session:
    print(session.run("RETURN 'connected' AS status").single()["status"])

driver.close()