# infra-mgr
infra-mgr is a component responsible for rolling out schemas from git repository to kubernetes.
It watches for changes in the repositories and deploys changed components to k8s.
Depending on the schema configuration, it also monitors kubernetes and if it detects external manipulation on deployed component, redeploys it from repository version.



## Configuration

## 
