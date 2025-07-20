package boxy.persistence.it;

import boxy.persistence.model.*;

record TestingData(Topic topic,
                   ConsumerGroup consumerGroup,
                   Subscription subscription,
                   SubscriptionOffset subscriptionOffset,
                   Worker worker1,
                   Worker worker2) {
}

