window.PolymerBoxes = {
  handleObserve: function (element, variable, oldValue, newValue) {
	//Make server data in element if we don't have it
	element.boxesServerData = element.boxesServerData || {};

    //Check that we have data, and we aren't doing this in response to a new value from the server
    //We do this by tracking the last index seen
    sd = element.boxesServerData[variable]
    if (sd) {
      if (sd.guid && sd.lastIndexSeen == sd.index) {
        console.log(oldValue + "->" + newValue)
        cmd = sd.guid + '=' + JSON.stringify({"value": newValue, "index": sd.index, "guid": sd.guid})
        console.log(cmd)
        liftAjax.lift_ajaxHandler(cmd, null, null, null)
      }
      sd.lastIndexSeen = sd.index;
    }
  },
  
  handleServerData: function(element, variable, data) {
	//Make server data in element if we don't have it
	element.boxesServerData = element.boxesServerData || {};

    //Note we set data (including index) before value, so that textFromBrowser will realise there is new server data when it is called
	element.boxesServerData[variable] = data;
    element[variable] = data.value;
  }
}