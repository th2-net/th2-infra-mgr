apiVersion: helm.fluxcd.io/v1
kind: HelmRelease
metadata:
  name: ${name}
spec:
  chart:
    git: https://github.com/vdonadze/job-chart
    path: chart
    ref: main
  values:
    cassandra:
      keyspacePrefix: ${cassandra.keyspacePrefix}
      username: ${cassandra.username}
      password: ${cassandra.password}
      host: ${cassandra.host}
      port: ${cassandra.port}
      datacenter: ${cassandra.datacenter}
      timeout: ${cassandra.timeout}
      instanceName: ${cassandra.instanceName}
      schemaNetworkTopology:
        <#list cassandra.schemaNetworkTopology as key, value>
        ${key}: ${value}
        </#list>
    keyspace:
      schemaVersion: ${keyspace.schemaVersion}
      initializer: ${keyspace.initializer}
