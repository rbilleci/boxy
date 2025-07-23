package boxy.core.service;

import boxy.core.dao.EventDao;
import org.jdbi.v3.core.Jdbi;

public class EventService {

    private final EventDao eventDao;

    public EventService(Jdbi jdbi) {
        this.eventDao = jdbi.onDemand(EventDao.class);
    }

    public void publish(String tenant, String topicName, String key, String data) {
        eventDao.publish(tenant, topicName, key, data);
    }

    public void publishAdvanced(long partitionId, String data) {
        eventDao.publishAdvanced(partitionId, data);
    }


}
