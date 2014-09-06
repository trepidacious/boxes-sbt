'use strict';

/* Controllers */

var userEdit = angular.module('userEdit', ['ui.bootstrap', 'angularBoxes', 'ngAnimate', 'ui.bootstrap.showErrors']);

userEdit.controller('UserEditCtrl', function ($scope) {
  
  $scope.passA = "";
  $scope.passB = "";
  $scope.passChanged = false;
  
  $scope.submitPass = function(){
    $scope.$broadcast('show-errors-check-validity');

    if ($scope.userEditPassForm.$valid) {
    	var newPass = {passA:$scope.passA, passB:$scope.passB};
    	
    	//TODO refactor this into something reusable - find out how to do this in angular and add to angularBoxes.
        var json = JSON.stringify(newPass);
        console.log($scope.submitPassGUID + '=' + json)
        liftAjax.lift_ajaxHandler($scope.submitPassGUID + '=' + json, null, null, null)
        
        $('#submit-pass-btn').button('loading')
    }

  };
  
});

