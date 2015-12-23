#!/bin/echo "This is only used as a library"

# $1=command
# $2=include local (a boolean flag)
loop_execute() {
    set +e
    local command=${1}
    local includeLocal=${2}
    local node_count=${3}
    local local_node=${4}
    local password=${5}

    for i in $(seq 1 ${NODE_COUNT})
    do
        local viprNode=$(get_nodeid)
        if [ "$viprNode" != "$LOCAL_NODE" -o "$includeLocal" == "true" ]; then
            ssh_execute "$viprNode" "$command" "${ROOT_PASSWORD}"&
        fi
    done
    wait
    set -e                                                                      
}

# $1=node name
# $2=command
ssh_execute() {
    local viprNode=${1}
    local command=${2}
    ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null svcuser@$viprNode "echo '${ROOT_PASSWORD}' | sudo -S $command" &>/dev/null
}

get_nodeid() {
    if [ ${NODE_COUNT} -eq 1 ]; then
        echo "${LOCAL_NODE}"
    fi

    echo "vipr$i"
}

finish_message() {
    echo "Restore ${RESTORE_RESULT}!"
    if [ "${RESTORE_RESULT}" == "failed" ]; then
        echo "Please check bkutils.log for the details."
        exit 1
    fi
    echo "Note: nodes will reboot if there is any change of property in this cluster."
    if [ "$IS_CONNECTED_VDC" == "true" ]; then
        if [ "$RESTORE_GEO_FROM_SCRATCH" == "false" ]; then
            echo "Please reconnect this vdc after the status of cluster is stable."
        fi
        echo "(If there is any vdc with version 2.1 in this geo federation, then you need to remove blacklist manually from other vdcs,"
        echo "by using this command: \"/opt/storageos/bin/dbutils geoblacklist reset <vdc short id>\")"
    fi    
}

clean_up() {
    local command="rm -rf $RESTORE_DIR"
    loop_execute "${command}" "true" "${NODE_COUNT}" "${LOCAL_NODE}" "${ROOT_PASSWORD}"
}

