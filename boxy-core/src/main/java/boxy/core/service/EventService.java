package boxy.core.service;

import boxy.core.dao.EventDao;

import javax.sql.DataSource;

public class EventService {

    private final EventDao eventDao;

    public EventService(DataSource dataSource) {
        this.eventDao = new EventDao(dataSource);
    }

    public void publish(String tenant, String topicName, String key, String data) {
        eventDao.publish(tenant, topicName, key, data);
    }

    public void publishAdvanced(long partitionId, String data) {
        eventDao.publishAdvanced(partitionId, data);
    }


}
