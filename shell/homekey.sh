#!/system/bin/sh
# Opens com.firehome the moment Home is pressed on the remote, instead of after Fire OS's 5 s hold.
#
# After a Home press Android holds back activity starts from apps for 5 seconds (app switch protection), so the
# launcher's own watcher (HomeWatcher) only gets its screen up then. The adb shell user is exempt (it holds
# STOP_APP_SWITCHES), so this script, run as shell, follows the same log line HomeWatcher does - ActivityManager's
# "START u0 {... cat=[android.intent.category.HOME] ... cmp=com.amazon.tv.launcher/...} from uid 1000" - and starts
# HomeActivity at once (~0.2 s). Only "from uid 1000" is a remote press: Settings and the "Fire TV" button start
# Amazon's home from the launcher's own uid and are left alone, and a long press (the quick menu) logs no such line.
# Amazon's home still starts underneath; it just never shows.
#
# Not getevent: it buffers its output into a pipe and let a press through only 1.8 s later. logcat's lines arrive
# within ~20 ms.
#
# Nothing restarts a shell process after a reboot, so Home Assistant starts it whenever the Fire TV comes online
# (automation "firetv_homekey"). Without it, HomeWatcher's 5 s path still works.
#
#   adb push shell/homekey.sh /data/local/tmp/   then   adb shell sh /data/local/tmp/homekey.sh start
#   stop:  adb shell sh /data/local/tmp/homekey.sh stop

SELF=/data/local/tmp/homekey.sh

case "$1" in
    start)
        # at most one copy; detached from the adb session, which would otherwise take it down when it closes
        if pgrep -f 'homekey.sh run' > /dev/null; then
            echo "homekey: already running"
        else
            setsid nohup sh "$SELF" run < /dev/null > /dev/null 2>&1 &
            # stay until it has left this session: Home Assistant's adb closes the session the moment this
            # returns, and a copy still on its way out went down with it
            for i in 1 2 3 4 5 6 7 8 9 10; do
                sleep 0.3
                if pgrep -f 'homekey.sh run' > /dev/null; then
                    sleep 0.5
                    echo "homekey: started"
                    exit 0
                fi
            done
            echo "homekey: failed to start"
            exit 1
        fi
        exit 0
        ;;
    stop)
        # the whole process group (the loop and its logcat); `pkill -f 'homekey.sh run'` typed into adb shell
        # matches that adb shell's own command line too and kills it first
        for pid in $(pgrep -f 'homekey.sh run'); do
            kill -TERM -- "-$pid" 2> /dev/null
        done
        echo "homekey: stopped"
        exit 0
        ;;
    run)
        ;;
    *)
        echo "usage: sh $SELF start|stop"
        exit 1
        ;;
esac

while true; do
    # -T 1: from the newest line on, so presses already in the buffer are not replayed
    logcat -v brief -T 1 ActivityManager:I '*:S' 2> /dev/null | while read -r line; do
        case "$line" in
            *"START u0"*"category.HOME"*"cmp=com.amazon.tv.launcher/"*"from uid 1000"*)
                # NEW_TASK | REORDER_TO_FRONT, as HomeWatcher starts it
                am start -n com.firehome/.HomeActivity -f 0x10020000 \
                    --ez home_pressed true --ez instant true > /dev/null 2>&1
                ;;
        esac
    done
    sleep 2
done
