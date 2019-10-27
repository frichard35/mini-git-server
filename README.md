# mini-git-server

A minimalist http git server with a webhook capability. It can replace 
[git-http-backend](https://git-scm.com/docs/git-http-backend) if you need webhooks and if self hosted 
versions of gitlab/gogs are overkill for you.

Based on the [Undertow](http://undertow.io) server and [JGit](https://www.eclipse.org/jgit/) servlet, 
which does almost all the job. This project add just a webhook feature and configure 2 kinds of 
authentications, 'Basic Auth' and an 'External Auth' based on request header.

## Configuration

The project can be configured with a java properties file or with environment variables.
The reference is [this file](src/main/resources/application-default.properties) which
contains all the properties with their default values.

## Sample Usage With Docker
```Bash
#Build the project
docker build -t mini-git-server .

#Launch
docker run -d -p 8080:8080 --name mgs -v mgs_config_vol:/app/config -v mgs_repos_vol:/app/repos mini-git-server

#User creation
docker exec -ti -w /app mgs java -jar /app/mini-git-server.jar user

#Project initialization
docker run -ti --rm -v mgs_repos_vol:/app/repos -w /app/repos -u 1035:1035 alpine/git init --bare --shared=group new_project.git
git clone http://localhost:8080/git/new_project.git

#Project import from github
docker run -ti --rm -v mgs_repos_vol:/app/repos -w /app/repos -u 1035:1035 alpine/git init --bare --shared=group project.git
git clone --bare https://github.com/user/project.git
cd project.git
git push --mirror http://localhost:8080/git/project.git
```

