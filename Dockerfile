# Build environment for fb-client. No Gradle: the final image holds only a jlink-trimmed Java
# runtime (javac + keytool) and the four build-tools pieces that are needed.

# 1. sdkmanager downloads build-tools and android.jar
FROM eclipse-temurin:21-jdk AS sdk
ARG CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl ca-certificates \
 && curl -fsSL -o /tmp/ct.zip https://dl.google.com/android/repository/${CMDLINE_TOOLS} \
 && mkdir -p /sdk/cmdline-tools && unzip -q /tmp/ct.zip -d /sdk/cmdline-tools \
 && mv /sdk/cmdline-tools/cmdline-tools /sdk/cmdline-tools/latest \
 && yes | /sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/sdk --licenses > /dev/null \
 && /sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/sdk "build-tools;36.0.0" "platforms;android-36" > /dev/null \
 && mkdir -p /bt/lib /bt/lib64 \
 && cd /sdk/build-tools/36.0.0 \
 && cp aapt2 zipalign /bt/ && cp lib/d8.jar lib/apksigner.jar /bt/lib/ \
 && cp -P lib64/libc++.so lib64/libc++.so.1 /bt/lib64/

# 2. minimal Java runtime
FROM eclipse-temurin:21-jdk AS jre
RUN jlink --add-modules java.base,java.compiler,jdk.compiler,java.logging,java.xml,jdk.unsupported,jdk.zipfs,jdk.crypto.ec,java.management \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output /jre

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends zip \
 && rm -rf /var/lib/apt/lists/*
COPY --from=jre /jre /jre
COPY --from=sdk /bt /sdk/build-tools
COPY --from=sdk /sdk/platforms/android-36/android.jar /sdk/android.jar
ENV PATH=/jre/bin:$PATH
WORKDIR /src
CMD ["sh", "/src/build.sh"]
