#!/bin/sh
set -e

# Render assigns a dynamic port via the $PORT environment variable (defaults to 8080)
PORT="${PORT:-8080}"

echo "[Render Entrypoint] Configuring Apache Tomcat to listen on port: $PORT"
sed -i "s/port=\"8080\"/port=\"$PORT\"/g" /usr/local/tomcat/conf/server.xml

echo "[Render Entrypoint] Launching Apache Tomcat 10.1..."
exec catalina.sh run
