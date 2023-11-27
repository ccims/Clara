package de.unistuttgart.iste.sqa.clara.aggregation.platform.kubernetes.aggregators.dns

import arrow.core.Either
import arrow.core.getOrElse
import de.unistuttgart.iste.sqa.clara.aggregation.platform.kubernetes.client.KubernetesClient
import de.unistuttgart.iste.sqa.clara.api.aggregation.AggregationFailure
import de.unistuttgart.iste.sqa.clara.api.aggregation.CommunicationAggregator
import de.unistuttgart.iste.sqa.clara.api.model.Communication
import de.unistuttgart.iste.sqa.clara.api.model.Namespace
import io.github.oshai.kotlinlogging.KotlinLogging

class KubernetesDnsAggregator(
    private val config: Config,
    private val kubernetesClient: KubernetesClient,
) : CommunicationAggregator {

    data class Config(
        val namespaces: List<Namespace>,
        val includeKubeNamespaces: Boolean,
    )

    private val log = KotlinLogging.logger {}

    override fun aggregate(): Either<AggregationFailure, Set<Communication>> {
        log.info { "Aggregate Kubernetes DNS ..." }

        val dnsLogs = kubernetesClient.getDnsLogs()
            .getOrElse { return Either.Left(AggregationFailure(it.description)) }
        val knownPods = kubernetesClient.getPodsFromNamespaces(config.namespaces, config.includeKubeNamespaces)
            .getOrElse { return Either.Left(AggregationFailure(it.description)) }
        val knownServices = kubernetesClient.getServicesFromNamespaces(config.namespaces, config.includeKubeNamespaces)
            .getOrElse { return Either.Left(AggregationFailure(it.description)) }

        val dnsQueries = KubernetesDnsLogAnalyzer.parseLogs(dnsLogs)
        val queryAnalyzer = KubernetesDnsQueryAnalyzer(knownPods, knownServices)

        return Either.Right(queryAnalyzer.analyze(dnsQueries))
    }
}
