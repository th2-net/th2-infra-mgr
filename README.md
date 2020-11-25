# infra-mgr
infra-mgr is a component responsible for rolling out schemas from git repository to kubernetes.
It watches for changes in the repositories and deploys changed components to kubernets.
Depending on the schema configuration, it also monitors kubernetes and if it detects external manipulation on deployed component, redeploys them from latest repository version.

### Schema
A collection of the interconnected components is called a schema and is contained in the single branch of the repository.
It is deployed on its own namespace in kubernetes and synchronization is done between repository branch and kubernetes namespace.

Schema is composed of kubernets Th2 Custom Resources (CR) of various kinds.
For every kind of the CR is designated separate root directory in the repository.
CR-s kubernetes yaml format files and are expected to be resided in its own directory.
Also CR-s file names(excluding extension) must match kubernetes resource names.

All schema and resource names must comply with DNS label names as defined in RFC 1123.
https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names


### Configuration

## 
