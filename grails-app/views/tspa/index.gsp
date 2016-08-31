<html ng-app="zenboottspaApp" >
<head>
    <meta charset="utf-8">
    <title>zenboot as Single Page Application</title>
    <!-- angular in version 1.2.1 -->
    <asset:javascript src="angular.min.js"></asset:javascript>
    <script>
        var zenboottspaApp = angular.module('zenboottspaApp', []);
        zenboottspaApp.controller('zenboottspaCtrl', function ( $scope, $http ){
            $scope.limit=1
            $http({
                method: 'GET',
                url: '/zenboot/rest/executionzones\?max\=100000',
                headers: {'Content-Type': "application/json"},
                data: "" // this is needed see http://stackoverflow.com/questions/24895290/content-type-header-not-being-set-with-angular-http
            }).success(function(data) {
                $scope.zones = data;
            });
        });
    </script>
    <asset:link rel="shortcut icon" href="favicon.ico" type="image/x-icon"/>
    <asset:stylesheet href="application.css"/>
</head>
<body ng-controller="zenboottspaCtrl" background="../assets/zenboot_bg.png" style="background-repeat:repeat;margin:0">
<input ng-model="query" type="text"/>
<span class="icon-chevron-up" ng-click="limit=limit+1"> &nbsp;</span>
<span class="icon-chevron-down" ng-click="limit=limit-1">&nbsp;</span>
<br/>


<table width="100%">
    <tr ng-repeat="zone in zones  | filter:query |limitTo: limit ">
        <td><a href="/zenboot/executionZone/show/{{zone.id}}" target="mainframe">{{zone.description}}</a></td>
        <td> <a  ng-repeat="url in zone.serviceUrls  | filter:query" href="{{url.url}}" target="_blank">{{url.url}} </td>
    </tr>

</table>
<iframe src="/zenboot" name="mainframe" frameborder="0" style="overflow:hidden;height:100%;width:100%;margin: 0px; padding:0px;" height="100%" width="150%"></iframe>

</body>
</html>
