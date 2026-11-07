package kr.sedanji;

import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

/** Applies to PostgreSQL and helper containers: verification never pulls images. */
public final class E2eCachedImagePolicy implements ImagePullPolicy {
    @Override public boolean shouldPull(DockerImageName name) { return false; }
}
