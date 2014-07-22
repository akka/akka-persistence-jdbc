sudo apt-get update
sudo apt-get -y install docker.io
sudo ln -sf /usr/bin/docker.io /usr/local/bin/docker
sudo sed -i '$acomplete -F _docker docker' /etc/bash_completion.d/docker.io
# java 7
sudo apt-get install openjdk-7-jdk -qqy
# java 8
#sudo add-apt-repository ppa:webupd8team/java -qqy
#sudo apt-get update
#sudo apt-get install oracle-java8-installer -qqy
#sudo apt-get install oracle-java8-set-default -qqy
