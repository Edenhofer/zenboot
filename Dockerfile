FROM codenvy/jdk7_tomcat7
ADD https://github.com/hybris/zenboot/releases/download/v0.7.55555/zenboot.war /home/user/tomcat7/webapps/zenboot.war
ADD zenboot.properties /etc/zenboot/zenboot.properties
RUN sudo apt-get update && sudo apt-get install -y curl ansible openssh-client sshpass socat dnsutils
RUN mkdir -p /home/user/zenboot

EXPOSE 8080
