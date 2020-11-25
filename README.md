# infra-mgr
infra-mgr is a component responsible for rolling out schemas from git repository to kubernetes.
It watches for changes in the repositories and deploys changed components to kubernets.
Depending on the schema configuration, it also monitors kubernetes and if it detects external manipulation on deployed component, redeploys them from latest repository version.

### Schema
A collection of the interconnected components is called a schema and is contained in the single branch of the repository.
It is deployed on its own namespace in kubernetes and synchronization is done between repository branch and kubernetes namespace.

Schema is composed of kubernets Th2 Custom Resources (CR) of various kinds.
For every kind of the CR is designated separate root directory in the repository.
CR-s are described by kubernetes yaml resource files and are expected to be resided in its own directory.
CR's file names(not icluding extension) must match kubernetes resource names.

All schema and resource names must comply with DNS label names as defined in RFC 1123.
https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names


See example shema repository for further description of its structure

### Configuration
infra-mgr configuration is given with config.yml file that should be on the classpath of the application

```yaml
    git:
      remoteRepository:  
      # git ssh repository URL
      
      localRepositoryRoot: 
      #path to folder where local copy of repository will be cached
      
      privateKeyFile: 
      #path to private key file to 
      
      ignoreInsecureHosts: true
      # set to true to connect to self signed or insecure servers

    rabbitmq:
      vhostPrefix: schema-
      usernamePrefix: schema-user-
      secret: rabbitmq
      passwordLength: 24

    cassandra:
      keyspacePrefix: schema_

    kubernetes:
      useCustomConfig: false
      namespacePrefix: schema-

      ingress: ingress-rules
      secretNames:
        - chart-secrets
        - git-chart-creds
        - th2-core
        - th2-solution
        - th2-proprietary
        - th2-schema-test
        - cassandra
      configMaps:
        cassandra: cradle
        logging: java-logging-config
        rabbitmq: rabbit-mq-app-config
        rabbitmqManagement: rabbitmq-mng-params

```
## 
