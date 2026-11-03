package org.khylo.tv.core.auth.model

import org.khylo.sdk.discovery.RecommendedServerIssue
import org.khylo.sdk.model.api.PublicSystemInfo
import java.util.UUID

sealed class ServerAdditionState
data class ConnectingState(val address: String) : ServerAdditionState()
data class UnableToConnectState(val addressCandidates: Map<String, Collection<RecommendedServerIssue>>) : ServerAdditionState()
data class ConnectedState(val id: UUID, val publicInfo: PublicSystemInfo) : ServerAdditionState()
