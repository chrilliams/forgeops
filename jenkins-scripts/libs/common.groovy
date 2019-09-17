/*
 * Copyright 2019 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

import com.forgerock.pipeline.stage.FailureOutcome
import com.forgerock.pipeline.stage.Outcome
import com.forgerock.pipeline.stage.Status

/*
 * Common configuration used by several stages of the ForgeOps pipeline.
 */

/**
 * Globally scoped git commit information
 */
FORGEOPS_SHORT_GIT_COMMIT = sh(script: 'git rev-parse --short=15 HEAD', returnStdout: true).trim()

/**
 * Globally scoped git commit information
 */
FORGEOPS_GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()

/**
 * Globally scoped git commit information for the Lodestar repo
 */
LODESTAR_GIT_COMMIT = '8e3b671a42ed932678353a70e36bf3c76bb87290'

/** Base product versions for the PIT#2 upgrade test */
UPGRADE_TEST_BASE_AMSTER_VERSION      = '7.0.0-a220039d37'
UPGRADE_TEST_BASE_AM_VERSION          = '7.0.0-a220039d37'
UPGRADE_TEST_BASE_IDM_VERSION         = '7.0.0-67e54db'
UPGRADE_TEST_BASE_CONFIGSTORE_VERSION = '7.0.0-bdc0ce8'
UPGRADE_TEST_BASE_USERSTORE_VERSION   = '7.0.0-bdc0ce8'


/** Root-level image names corresponding to product Helm charts and Dockerfiles in the ForgeOps repo. */
ROOT_LEVEL_IMAGE_NAMES = [
        'am'     : 'gcr.io/forgerock-io/am',
        'am-fbc' : 'gcr.io/forgerock-io/am',
        'amster' : 'gcr.io/forgerock-io/amster',
        'ds'     : 'gcr.io/forgerock-io/ds',
        'idm'    : 'gcr.io/forgerock-io/idm',
        'ig'     : 'gcr.io/forgerock-io/ig',
]

/** Helm chart file paths. Should be treated as private, although it's not possible to enforce this in Groovy. */
HELM_CHART_PATHS = [
        'am'     : 'helm/openam/values.yaml',
        'amster' : 'helm/amster/values.yaml',
        'ds'     : 'helm/ds/values.yaml',
        'idm'    : 'helm/openidm/values.yaml',
        'ig'     : 'helm/openig/values.yaml',
]

/**
 * Helm data relevant to the ForgeOps pipeline. Cached to prevent repeated reading from file.
 * Should be treated as private, although it's not possible to enforce this in Groovy.
 */
helmChartCache = [:]

/** Products which have associated Helm charts. */
Collection<String> getHelmChartProductNames() {
    return HELM_CHART_PATHS.keySet()
}

/** Helm Chart data for all ForgeRock products. */
Collection<Map> getHelmCharts() {
    return getHelmChartProductNames().collect { getHelmChart(it) }
}

/**
 * Helm chart data for individual ForgeRock product.
 *
 * @param productName Product to retrieve Helm chart data for.
 * @return Helm chart data relevant to the build pipelines.
 */
Map getHelmChart(String productName) {
    if (!HELM_CHART_PATHS.containsKey(productName)) {
        error "Unknown Helm chart for '${productName}'"
    }
    if (!ROOT_LEVEL_IMAGE_NAMES.containsKey(productName)) {
        error "Unknown root-level image name '${productName}'"
    }

    def helmChartFilePath = HELM_CHART_PATHS[productName]

    if (!helmChartCache.containsKey(productName)) {
        // cache Helm chart data for future use
        def helmChartYaml = readYaml(file: helmChartFilePath)
        helmChartCache[productName] = [
            'filePath'           : helmChartFilePath,
            'rootLevelImageName' : ROOT_LEVEL_IMAGE_NAMES[productName],
            'currentImageName'   : helmChartYaml.image.repository,
            'currentTag'         : helmChartYaml.image.tag,
            'productCommit'      : helmChartYaml.image.tag.split('-').last(),
        ]
    }

    return helmChartCache[productName]
}

/** Skaffold Dockerfile paths. Should be treated as private, although it's not possible to enforce this in Groovy. */
SKAFFOLD_DOCKERFILE_PATHS = [
        'am':     'docker/am/Dockerfile',
        'am-fbc': 'docker/am-fbc/Dockerfile',
        'amster': 'docker/amster/Dockerfile',
        // ds-empty does not get promoted, as we have no tests for it yet
        'idm':    'docker/idm/Dockerfile',
        'ig':     'docker/ig/Dockerfile',
]

/** Products which have associated Dockerfiles. */
Collection<String> getDockerfileProductNames() {
    return SKAFFOLD_DOCKERFILE_PATHS.keySet()
}

/** Skaffold Dockerfile data for all ForgeRock products. */
Collection<Map> getDockerfiles() {
    return getDockerfileProductNames().collect { getDockerfile(it) }
}

/**
 * Skaffold Dockerfile data for individual ForgeRock product.
 *
 * @param productName Product to retrieve Dockerfile data for.
 * @return Dockerfile data relevant to the build pipelines.
 */
Map getDockerfile(String productName) {
    if (!SKAFFOLD_DOCKERFILE_PATHS.containsKey(productName)) {
        error "Unknown Dockerfile for '${productName}'"
    }
    if (!ROOT_LEVEL_IMAGE_NAMES.containsKey(productName)) {
        error "Unknown root-level image name '${productName}'"
    }

    String tag = productName == 'am-fbc' ? getHelmChart('am').currentTag : getHelmChart(productName).currentTag

    return [
            'filePath'     : SKAFFOLD_DOCKERFILE_PATHS[productName],
            'fullImageName': "${ROOT_LEVEL_IMAGE_NAMES[productName]}:${tag}",
    ]
}

def normalizeStageName(String stageName) {
    return stageName.toLowerCase().replaceAll("\\s","-")
}

def getCurrentProductCommitHashes() {
    return [
            getHelmChart('ds').productCommit,
            getHelmChart('ig').productCommit,
            getHelmChart('idm').productCommit,
            getHelmChart('am').productCommit,
    ]
}

def determinePitOutcome(String reportUrl, Closure process) {
    try {
        process()
        return new Outcome(Status.SUCCESS, reportUrl)
    } catch (Exception e) {
        return new FailureOutcome(e, reportUrl)
    }
}

def determinePyrockOutcome(String reportUrl, Closure process) {
    try {
        process()
        return new Outcome(Status.SUCCESS, reportUrl)
    } catch (Exception e) {
        return new FailureOutcome(e, reportUrl)
    }
}

return this
