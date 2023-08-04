package org.jolokia.kubernetes.client;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.fabric8.kubernetes.api.model.NamedCluster;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientImpl;
import io.fabric8.kubernetes.client.utils.Serialization;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.testng.annotations.Test;

public class ManualKubernetesConnection {

    @Test(groups = "manual")
    public void testKubernetesClient() {
        // default configuration uses default context/cluster/user from ~/.kube/config
        // if the API server is not set, io.fabric8.kubernetes.client.Config.masterUrl is used
        // which is https://kubernetes.default.svc
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            System.out.println("Version: " + client.getKubernetesVersion().getGitVersion());
            for (Namespace ns : client.namespaces().list().getItems()) {
                System.out.println("Namespace: " + ns.getMetadata().getName());
            }
        }
    }

    @Test(groups = "manual")
    public void testKubernetesOkHttpClient() throws IOException {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            HttpClient httpClient = client.getHttpClient();

            // get OkHttpClient from KubernetesClient
            OkHttpClient okHttpClient = ((OkHttpClientImpl) httpClient).getOkHttpClient();

            // OkHttp stuff
            HttpUrl.Builder urlBuilder = new HttpUrl.Builder();

            URL masterApiUrl = client.getMasterUrl();
            urlBuilder.scheme(masterApiUrl.getProtocol());
            urlBuilder.host(masterApiUrl.getHost());
            if (masterApiUrl.getPort() != -1) {
                urlBuilder.port(masterApiUrl.getPort());
            }
            urlBuilder.encodedPath("/version");

            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(urlBuilder.build().url());

            try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
                System.out.println("Result: " + response.code() + "/" + response.message());
                if (response.body() != null) {
                    System.out.println("Response body: " + response.body().string());
                }
            }
        }
    }

    @Test(groups = "manual")
    public void testKubernetesConfig() throws IOException {
        byte[] bytes = Files.readAllBytes(new File(Config.getKubeconfigFilename()).toPath());
        io.fabric8.kubernetes.api.model.Config cfg = Serialization.unmarshal(new String(bytes, StandardCharsets.UTF_8), io.fabric8.kubernetes.api.model.Config.class);
        if (cfg.getClusters() != null) {
            for (NamedCluster cl : cfg.getClusters()) {
                System.out.println("Cluster: " + cl.getCluster().getServer());
            }
        }
    }

}
