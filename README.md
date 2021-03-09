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
infra-mgr configuration is given with *config.yml* file that should be on the classpath of the application

```yaml
    git:
      remoteRepository: git@some.server.com:some/repository
      # git ssh repository URL
      
      localRepositoryRoot: /path/to/cache
      # path to folder where local copy of repository will be cached
      # separate local repository will be created for every branch of remote repository
      
      privateKeyFile: /path/to/key
      # path to private key file to be used for authentication
      # due to underlayng library restrictions, only RSA keys generated in PEM mode are supported
      
      ignoreInsecureHosts: true
      # set to true to connect to self signed or insecure servers

      # For following configs to work
      # Http link should be provided in `remoteRepository' config

      httpAuthUsername: username
      # authentication username
      # when using token auth for GitLab it should be equal to "oauth2"
      # when using token auth for GitHub it should be equal to token itself
      httpAuthPassword: password
      # authentication password
      # when using token auth for GitLab it should be equal to token itself
      # when using token auth for GitHub it should be equal to empty string

    rabbitmq:
      vhostPrefix: schema-
      # this prefix will be prepended to every vHost in RebbitMQ that will be automatically
      # created by infra-operator
      
      usernamePrefix: schema-user-
      # when creating indiviadual user for a schema in RabbitMQ, this prefix will be prepended to schema name
      # to form a username

      secret: rabbitmq
      # secret name for generated RabiitMQ username and password to be created
      # in schema namespace
      
      passwordLength: 24
      # RabbitMQ password will be generated with this length

    cassandra:
      keyspacePrefix: schema_
      # this parameter will be prepended to schema name and will be used as a 
      # keyspace name in cassandra for the schema
      
    kubernetes:
      namespacePrefix: schema-
      # this parameter will be prepended to schema name and will be used as a 
      # namespace name for the schema

      ingress: ingress-name
      # name of the ingress HelmRelease to be copied from infra-mgr namespace to schema namespace
      # when deploying schema to kubernetes

      secretNames:
        - chart-secrets
        - git-chart-creds
        - th2-core
        - th2-solution
        - th2-proprietary
        - th2-schema-test
        - cassandra
      # list of the secrets to be copied from infra-mgr namespace to schema namespace
      # when deploying schema to kubernetes

      configMaps:
        cassandra: cradle
        logging: java-logging-config
        rabbitmq: rabbit-mq-app-config
        rabbitmqManagement: rabbitmq-mng-params
      # individual ConfigMaps for components to be copied from infra-mgr namespace to schema namespace
      # this ConfigMaps will be populated with schema specific data before copying to target namespace

```
##
## For API documentation please refer to
[API Documentation](API.md)