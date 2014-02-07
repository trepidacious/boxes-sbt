'use strict';

/* Controllers */

var demoApp = angular.module('demoApp', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);
 
demoApp.controller('DemoCtrl', function ($scope) {
  $scope.entries = [];
  $scope.inOutSetting = 'either';

  $scope.testString = "Bill";
  $scope.testStringGUID = null;
  $scope.$watch('testString', function(current, previous){
	console.log("testString changed from " + previous + " to " + current + ", guid is " + $scope.testStringGUID)
    if (current != previous && $scope.testStringGUID) {
      liftAjax.lift_ajaxHandler($scope.testStringGUID + '=' + current, null, null, null)
    }
  }, true)
  
  $scope.remove = function(deleteGUID){
//    alert("Delete with GUID " + deleteGUID);
    liftAjax.lift_ajaxHandler(deleteGUID + '=true', null, null, null)
  };

  
});
