'use strict';

/* Controllers */

var userSignup = angular.module('userSignup', ['ui.bootstrap', 'angularBoxes', 'ngAnimate']);

userSignup.controller('UserSignupCtrl', function ($scope) {
  $scope.firstName = "";
  $scope.lastName = "";
  $scope.email = "";
  $scope.initials = "";
  $scope.passA = "";
  $scope.passB = "";
//  $scope.passAProblem = "pap";
//  $scope.passBProblem = "pbp";
  
//  // watch the password field and grade it.
//  $scope.$watch('[passA, passB]', function() {
//	  if ($scope.passA != $scope.passB) {
//		  $scope.passBProblem = "Passwords do not match"
//      } else {
//    	  $scope.passBProblem = ""    	  
//      }
//      if ($scope.passA.length < 8) {
//    	  $scope.passAProblem = "Password is too short"
//      } else {
//    	  $scope.passAProblem = ""    	  
//      }
//  });
  
});

