package org.khylo.sdk.api.client.exception

public class MissingBaseUrlException(
	cause: Throwable? = null,
) : ApiClientException(
	"Required value baseUrl is null. Provide it by setting ApiClient.baseUrl.",
	cause
)
