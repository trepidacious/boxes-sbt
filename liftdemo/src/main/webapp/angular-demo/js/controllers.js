'use strict';

/* Controllers */

var demoApp = angular.module('demoApp', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);

demoApp.controller('DemoCtrl', function ($scope) {
  $scope.entries = [];
  $scope.inOutSetting = 'either';

  $scope.remove = function(deleteGUID){
    liftAjax.lift_ajaxHandler(deleteGUID + '=true', null, null, null)
  };
  
});
