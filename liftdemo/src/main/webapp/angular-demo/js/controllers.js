'use strict';

/* Controllers */

var demoApp = angular.module('demoApp', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);
 
demoApp.controller('DemoCtrl', function ($scope) {
  $scope.entries = [];
  $scope.inOutSetting = 'either';

  $scope.testString = {value: "", index: -1, guid: null};
  
  $scope.$watch('testString', function(current, previous){
	console.log("testString changed from " + previous.value + " to " + current.value + ", guid is " + current.guid)
    if ((current.value != previous.value) && (current.index == previous.index) && current.guid) {
      liftAjax.lift_ajaxHandler(current.guid + '={"value": "' + current.value + '", "index": ' + current.index + '}', null, null, null)
    }
  }, true)
  
  $scope.remove = function(deleteGUID){
    liftAjax.lift_ajaxHandler(deleteGUID + '=true', null, null, null)
  };

  
});
