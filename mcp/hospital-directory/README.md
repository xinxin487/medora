# Hospital Directory MCP Demo

Build the executable JAR with `mvn clean test package`, then build the image from this directory.

The server exposes Streamable HTTP MCP on `/mcp`. Set `HOSPITAL_DIRECTORY_TOKEN` through `hospital-directory.token` and allow the Medora Origin through `hospital-directory.allowed-origin`; the demo returns only the read-only `findDepartment` tool.
