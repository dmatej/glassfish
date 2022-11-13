FROM eclipse-temurin:17.0.5_8-jdk

# HOWTO:
#
# 1) cd etc/jenkins
# 4) make # takes aroung 5 minutes, depending on network speed
# 5) docker login
# 6) docker push dmatej/glassfish-builder:17.0.5_8 or another repository, don't forget to update the Jenkinsfile and Makefile too.
# 7) docker logout
# 8) git commit, git push, run jenkins build, etc.
#

# libxtst6 etc. - dependencies of JDK, required for running webservices12 and webservices13 TCK tests.
USER root
RUN true \
    && apt update \
    && apt install -q -y openssh-client ca-certificates apt-utils docker procps git curl gnupg lsb-core tini zip unzip libxtst6 libfontconfig1 libxrender1 libxi6

RUN true \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update \
    && apt-get install -q -y docker-ce-cli \
    && apt-get install -q -y && apt-get -q -y clean \
    && wget -nv https://dlcdn.apache.org/ant/binaries/apache-ant-1.9.16-bin.zip && unzip -q apache-ant-1.9.16-bin.zip && rm apache-ant-1.9.16-bin.zip \
    && wget -nv https://dlcdn.apache.org/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.zip && unzip -q apache-maven-3.8.6-bin.zip && rm apache-maven-3.8.6-bin.zip \
    && true

RUN pwd && ls -la \
    && groupadd -r docker -g 998 \
    && useradd -U -d /home/jenkins -s /bin/bash jenkins \
    && usermod -aG docker jenkins \
    && mkdir /home/jenkins \
    && chown -R jenkins:jenkins /home/jenkins \
    && true

USER jenkins
WORKDIR /home/jenkins

RUN true \
    && mkdir -p "/home/jenkins/.ssh" \
    && chmod go-rwx "/home/jenkins/.ssh" \
    && ssh-keyscan -H github.com >> "/home/jenkins/.ssh/known_hosts" \
    && cat "/etc/hosts" \
    && bash -c "env | sort; java -version && /apache-maven-3.8.6/bin/mvn -version && /apache-ant-1.9.16/bin/ant -version && docker -v" \
    && echo "Installation was successful."

ENV JAVA_HOME /opt/java/openjdk
ENV MAVEN_HOME /apache-maven-3.8.6
ENV ANT_HOME /apache-ant-1.9.16

ENV HOME /home/jenkins
ENV PATH "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${ANT_HOME}/bin:${PATH}"

# Obsoleted - but used.
ENV M2_HOME ${MAVEN_HOME}

ENTRYPOINT ["/usr/bin/tini", "--"]
