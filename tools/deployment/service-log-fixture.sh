#!/bin/sh
echo 'POST /api/login HTTP/1.1 500 Internal Server Error'
echo 'Traceback: database connection refused'
echo 'password=fixture-private-password'
counter=0
while [ "$counter" -lt 400 ]; do
  echo 'GET /health HTTP/1.1 200 OK'
  counter=$((counter + 1))
done
