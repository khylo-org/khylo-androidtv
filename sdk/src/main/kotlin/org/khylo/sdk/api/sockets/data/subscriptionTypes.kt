package org.khylo.sdk.api.sockets.data

import org.khylo.sdk.model.api.ActivityLogEntryMessage
import org.khylo.sdk.model.api.ActivityLogEntryStartMessage
import org.khylo.sdk.model.api.ActivityLogEntryStopMessage
import org.khylo.sdk.model.api.OutboundWebSocketMessage
import org.khylo.sdk.model.api.ScheduledTasksInfoMessage
import org.khylo.sdk.model.api.ScheduledTasksInfoStartMessage
import org.khylo.sdk.model.api.ScheduledTasksInfoStopMessage
import org.khylo.sdk.model.api.SessionsMessage
import org.khylo.sdk.model.api.SessionsStartMessage
import org.khylo.sdk.model.api.SessionsStopMessage

/**
 * All socket message types that require a subscription. Each type contains the message type and start/stop message
 * constructors. If a message type does not exist in this set it does not need a start/stop message.
 */
internal val SUBSCRIPTION_TYPES: Set<SubscriptionType<out OutboundWebSocketMessage>> = setOf(
	subscriptionType<SessionsMessage>(
		{ period -> SessionsStartMessage(period.toString()) },
		{ SessionsStopMessage() }
	),

	subscriptionType<ActivityLogEntryMessage>(
		{ period -> ActivityLogEntryStartMessage(period.toString()) },
		{ ActivityLogEntryStopMessage() }
	),

	subscriptionType<ScheduledTasksInfoMessage>(
		{ period -> ScheduledTasksInfoStartMessage(period.toString()) },
		{ ScheduledTasksInfoStopMessage() }
	),
)
