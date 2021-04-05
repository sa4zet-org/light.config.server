# Lightweight configuration server using HOCON files

## Pull latest image

`docker pull sa4zet/light.config.server`

## The config_server_basic_auth_salt environment variable

You have to set that environment variable when you generate user password hash-es or running the application. It is a safer way than store your salt in your configuration file.

## The config_server_config_path environment variable

You have to set that environment variable to point of your custom configuration file.

## Generate user password hashes

```
docker run \
--rm \
-it \
--env="config_server_basic_auth_salt=example salt" \
sa4zet/light.config.server digest
```

You can terminate the hash generating process with `CTRL+D`.

```
Enter a not empty password: test.password
5FPkYtNi16s5bLp17Ystof8LpG5IXELksn7MzTPHnh+hreV17uM1Z4Hm+OWfKl0EIYzs3UFjctsY3XqfT8i/4g==
Enter a not empty password: another.password
ujj9wmKKq5ncyWg1VVZOcj3JcijjvHpqLjdEB3br5tYL2C3c+791Hmzz2dsQwDcWMSa1ScEXJMfA4mOlI0qfcA==
Enter a not empty password:
Bye!
```

## Example Configuration

https://github.com/sa4zet-org/light.config.server/blob/master/example.conf

## Run

```
docker run \
--detach \
--name="light.config.server" \
--env="config_server_basic_auth_salt=example salt" \
--env="config_server_config_path=/app.conf" \
--mount="type=bind,readonly,source=/tmp/example.conf,destination=/app.conf" \
--publish="0.0.0.0:80:5454/tcp" \
sa4zet/light.config.server

c7c93042fd388f305c64be9cc997a4bf0647293b3d535af6deb81e1dfab13302
```

```
docker ps

CONTAINER ID   IMAGE                        COMMAND                  CREATED         STATUS                   PORTS                  NAMES
c7c93042fd38   sa4zet/light.config.server   "java -jar /app/ktor…"   4 minutes ago   Up 4 minutes (healthy)   0.0.0.0:80->5454/tcp   light.config.server
```

## Config remote git repository

It has to follow the directory structure below:

```
.
├── dev -> separator directory to easily use different environments
│   ├── parent.conf -> HOCON file
│   ├── child_1.conf -> HOCON file
│   └── child_2.conf -> HOCON file
├── ignored.txt -> theese files are not used by the server
└── README.md -> theese files are not used by the server
``` 

You can read configuration file with GET method.

`curl -X GET "http://example.user:test.password@localhost/cfg/master/dev/child_1"`

You can read configuration subtree with GET method.

`curl -X GET "http://example.user:test.password@localhost/cfg/other_git_branch/dev/child_1/a/b"`

# License

https://github.com/sa4zet-org/light.config.server/blob/master/LICENSE