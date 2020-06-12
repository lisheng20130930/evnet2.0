#!/bin/bash 
cd /home

function stop(){
   PID=`ps -ef | grep TestServer.jar| grep -v grep   | awk '{print $2 }'`
   kill -TERM $PID
}

function start(){
    java -Xms128m -Xmx128m -jar TestServer.jar &
}

case "$1" in
    start)
      start
      exit $?
      ;;

    stop)
      stop
      exit $?
      ;;

    restart)
            stop
            sleep 2
            start
      exit $?
      ;;

    *)
      echo "Unkown command: \`$1'"
      echo "Usage: $PROGRAM ( commands ... )"
      echo "commands:"
      echo "  start             start server"
      echo "  stop              stop server"
      echo "  restart           restart server"
      exit 1
    ;;
esac
