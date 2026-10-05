#!/usr/bin/env bash
docker exec -e MYSQL_PWD=academia123 banco-mysql mysql --default-character-set=utf8mb4 -uacademia banco_db -t -e "$1"
