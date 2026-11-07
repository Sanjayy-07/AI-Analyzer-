# ==============================================================================
# Stage 1: Build the Maven Reactor (ArchAnalyzer Core + WebApp)
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Cache Maven dependencies by copying pom files first
COPY pom.xml .
COPY core/pom.xml core/
COPY webapp/pom.xml webapp/
RUN mvn dependency:go-offline -B || true

# Copy full source trees
COPY core core
COPY webapp webapp

# Compile and package WAR without executing unit tests during container build
RUN mvn clean package -DskipTests

# ==============================================================================
# Stage 2: Runtime Environment using Apache Tomcat 10.1 (Jakarta EE 10 on JDK 17)
# ==============================================================================
# NOTE: A full JDK image (not JRE) is required for OpenJDK Compiler Tree API
FROM tomcat:10.1-jdk17-temurin

WORKDIR /usr/local/tomcat

# Remove default demo Tomcat applications
RUN rm -rf webapps/*

# Deploy archanalyzer.war as ROOT.war to serve at the root context ("/")
COPY --from=builder /build/webapp/target/archanalyzer.war webapps/ROOT.war

# Copy startup entrypoint script to dynamically bind to Render's $PORT
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Memory tuning for Render Free Tier (512 MB RAM limit)
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseG1GC -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
