'use strict';

/* Controllers */

var userSignup = angular.module('userSignup', ['ui.bootstrap', 'angularBoxes', 'ngAnimate', 'ui.bootstrap.showErrors']);

userSignup.controller('UserSignupCtrl', function ($scope) {
  
  $scope.firstName = "";
  $scope.lastName = "";
  $scope.email = "";
  $scope.initials = "";
  $scope.passA = "";
  $scope.passB = "";
  $scope.made = false;
  
  $scope.submit = function(){
    $scope.$broadcast('show-errors-check-validity');

    if ($scope.userSignupForm.$valid) {
    	var user = {firstName:$scope.firstName, lastName:$scope.lastName, email:$scope.email, initials:$scope.initials, passA:$scope.passA, passB:$scope.passB};
    	
    	//TODO refactor this into something reusable - find out how to do this in angular and add to angularBoxes.
        var json = JSON.stringify(user);
        console.log($scope.submitGUID + '=' + json)
        liftAjax.lift_ajaxHandler($scope.submitGUID + '=' + json, null, null, null)
    }

  };
  
});

