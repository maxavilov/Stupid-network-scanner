#Stupid network scanner

Хотя эта "программа" и выполняет функции сетевого сканера, она ни в коей мере не
предназначена для замены nmap или чего-то подобного.

Это "Тупой сетевой сканер"!

Её предназначение - не сканировать сети, а служить шаблоном для
связки Google Web Toolkit и web socket.

##Что да где...

###pom.xml
Здесь собрано все, что нужно для GWT и web socket, а так же:
* простейшая обработка JSON при помощи org.json
* сборка всего этого дела в jar WildFly Swarm

####WildFly Swarm
Да. Все это можно собрать в один jar (с Java EE сервером). Собираем:
>mvn package

и получаем на выходе `NetworkScanner-1.0-SNAPSHOT-swarm.jar`.
Последний можно просто запустить
>java -jar NetworkScanner-1.0-SNAPSHOT-swarm.jar

После чего в браузере идем на [http://localhost:8080](http://localhost:8080).
При желании можно поменять как порт, так и ограничить адреса, на которых
будет работать сервер сканера (по умолчанию это адрес 0.0.0.0, т.е.
сканер доступен со всех интерфейсов). Настроить все это можно через
property при помощи опции -D java. Доступные для настройки property в
[документации к WildFly Swarm](https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/2017.2.0/configuration_properties.html) (хотя кому это нужно?).

###ru.amn.networkscanner.client.NetworkScanner.java
Клиентская GWT часть сканера, которая получает данные от сервера через web socket по мере сканирования сети.

###ru.amn.networkscanner.server.NetworkScannerWebSocketService.java
Серверная часть web socket. Выполнена стандартными средствами Java EE 7 (стандартный web socket endpoint).
На каждую сессию создается отдельный тред, в нем синхронная очередь для запросов.
Для каждого запроса создается массив (ArrayList) из 253 адресов, которые параллельно проверяются
при помощи ICMP ECHO REQUEST. О каждом ответившем на ICMP ECHO REQUEST хосте сообщается клиенту.

That's All Folks!



