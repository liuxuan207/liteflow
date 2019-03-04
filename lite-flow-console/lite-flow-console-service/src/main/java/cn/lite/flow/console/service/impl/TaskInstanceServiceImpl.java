package cn.lite.flow.console.service.impl;

import cn.lite.flow.common.model.consts.CommonConstants;
import cn.lite.flow.common.utils.DateUtils;
import cn.lite.flow.console.dao.mapper.TaskInstanceMapper;
import cn.lite.flow.console.model.basic.TaskInstance;
import cn.lite.flow.console.model.consts.TaskVersionStatus;
import cn.lite.flow.console.model.query.TaskInstanceQM;
import cn.lite.flow.console.service.DirectExecutorService;
import cn.lite.flow.console.service.TaskInstanceService;
import cn.lite.flow.executor.client.ExecutorJobRpcService;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Created by luya on 2018/7/23.
 */
@Service
public class TaskInstanceServiceImpl implements TaskInstanceService {

    @Autowired
    private TaskInstanceMapper taskInstanceMapper;

    @Autowired
    private ExecutorJobRpcService executorJobRpcService;

    @Autowired
    private DirectExecutorService directExecutorService;

    @Override
    public void add(TaskInstance model) {
        taskInstanceMapper.insert(model);
    }

    @Override
    public TaskInstance getById(long id) {
        return taskInstanceMapper.getById(id);
    }

    @Override
    public int update(TaskInstance instance) {
       return taskInstanceMapper.update(instance);
    }

    @Override
    public int count(TaskInstanceQM queryModel) {
        return taskInstanceMapper.count(queryModel);
    }

    @Override
    public List<TaskInstance> list(TaskInstanceQM queryModel) {
        return taskInstanceMapper.findList(queryModel);
    }

    @Override
    public List<TaskInstance> getByIds(List<Long> instanceIds) {

        if(CollectionUtils.isEmpty(instanceIds)){
            return null;
        }
        List<TaskInstance> tasks = Lists.newArrayList();

        List<List<Long>> partitionIds = Lists.partition(instanceIds, CommonConstants.LIST_BATCH_SIZE);
        partitionIds.forEach(ids -> {
            TaskInstanceQM qm = new TaskInstanceQM();
            qm.setIds(ids);
            List<TaskInstance> taskList = taskInstanceMapper.findList(qm);
            if(CollectionUtils.isNotEmpty(taskList)){
                tasks.addAll(taskList);
            }
        });
        return tasks;
    }

    @Override
    @Transactional("consoleTxManager")
    public boolean kill(long instanceId) {
        return this.kill(instanceId, true);
    }

    @Override
    @Transactional("consoleTxManager")
    public boolean kill(long instanceId, boolean isExecutorCallback) {
        TaskInstance taskInstance = taskInstanceMapper.getById(instanceId);

        TaskInstance updateInstance = new TaskInstance();
        updateInstance.setId(instanceId);
        updateInstance.setStatus(TaskVersionStatus.KILLED.getValue());
        taskInstanceMapper.updateWithStatus(updateInstance, taskInstance.getStatus());
        /**
         * kill executorJob
         */
        Long executorJobId = taskInstance.getExecutorJobId();
        if(executorJobId != null){
            directExecutorService.killJob(executorJobId, isExecutorCallback);
        }
        return true;
    }

    @Override
    public int updateWithStatus(TaskInstance instance, int status) {
        return taskInstanceMapper.updateWithStatus(instance, status);
    }

    @Override
    public int updateMsg(long id, String msg) {
        TaskInstance msgUpdateInstance = new TaskInstance();
        msgUpdateInstance.setId(id);
        msgUpdateInstance.setMsg(msg);
        return taskInstanceMapper.update(msgUpdateInstance);
    }

    @Override
    public List<Long> listReady2RunInstance() {
        TaskInstanceQM qm = new TaskInstanceQM();
        qm.setStatus(TaskVersionStatus.INIT.getValue());
        qm.setLogicRunTimeLessEqual(DateUtils.getNow());
        qm.addOrderAsc(TaskInstanceQM.COL_LOGIC_RUN_TIME);
        return taskInstanceMapper.findIdList(qm);
    }

    @Override
    public List<Long> listReady2SubmitInstance() {
        TaskInstanceQM qm = new TaskInstanceQM();
        qm.setStatus(TaskVersionStatus.READY.getValue());
        qm.addOrderAsc(TaskInstanceQM.COL_ID);
        return taskInstanceMapper.findIdList(qm);
    }

    @Override
    public List<Long> listSubmitedInstance() {
        TaskInstanceQM qm = new TaskInstanceQM();
        qm.setStatus(TaskVersionStatus.SUBMITTED.getValue());
        qm.addOrderAsc(TaskInstanceQM.COL_ID);
        return taskInstanceMapper.findIdList(qm);
    }

    @Override
    public TaskInstance getLatestInstance(Long taskId, Long taskVersionNo) {
        return taskInstanceMapper.getLatestInstance(taskId, taskVersionNo);
    }

    @Override
    public int statis(String startTime, String endTime, Integer status) {
        return Optional.ofNullable(taskInstanceMapper.statis(startTime, endTime, status)).orElse(0);
    }

    @Override
    public String getLog(long instanceId) {
        TaskInstance instance = taskInstanceMapper.getById(instanceId);
        Long executorJobId = instance.getExecutorJobId();
        if(executorJobId == null){
            return "";
        }
        return directExecutorService.getLog(executorJobId);
    }

}
