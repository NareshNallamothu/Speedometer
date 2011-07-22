#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

import logging

from django.utils import simplejson as json
from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import model
from gspeedometer.helpers import error
from gspeedometer.helpers import util

MEASUREMENT_TYPES = [('ping', 'ping'),
                     ('dns_lookup', 'DNS lookup'),
                     ('traceroute', 'traceroute'),
                     ('http', 'HTTP get'),
                     ('ndt', 'NDT measurement')]


class Measurement(webapp.RequestHandler):
  """Measurement request handler."""

  def PostMeasurement(self, **unused_args):
    """Handler used to post a measurement from a device."""
    if self.request.method.lower() != 'post':
      raise error.BadRequest('Not a POST request.')

    logging.info('PostMeasurement: request is ' + str(self.request))
    logging.info('PostMeasurement: body is ' + self.request.body)

    measurement_list = json.loads(self.request.body)

    for measurement_dict in measurement_list:
      logging.info('Device ID: ' + measurement_dict['device_id'])
      device_info = model.DeviceInfo.get_or_insert(
          measurement_dict['device_id'])

      # Write new device properties, if present
      if 'properties' in measurement_dict:
        device_properties = model.DeviceProperties()
        device_properties.device_info = device_info
        properties_dict = measurement_dict['properties']
        util.ConvertFromDict(device_properties, properties_dict)
        # Don't want the embedded properties in the Measurement object
        del measurement_dict['properties']
      else:
        # Get most recent device properties
        device_properties = device.GetLatestDeviceProperties(
            device_info, create_new_if_none=True)
      device_properties.put()

      measurement = model.Measurement()
      util.ConvertFromDict(measurement, measurement_dict)
      measurement.device_properties = device_properties
      measurement.put()
      logging.info('Stored measurement: ' + str(measurement))
      logging.info('Device: ' + str(measurement.device_properties))

    response = {'success': True}
    self.response.headers['Content-Type'] = 'application/json'
    self.response.out.write(json.dumps(response))

  def MeasurementDetail(self, **unused_args):
    """Handler to display measurement detail."""
    try:
      measid = self.request.get('id')
      measurement = model.Measurement.get_by_id(int(measid))
      if not measurement:
        errormsg = 'Cannot get measurement ' + measid
        template_args = {
            'error': errormsg,
            'user': users.get_current_user().email(),
            'logout_link': users.create_logout_url('/')
        }
        return
    finally:
      template_args = {
          'id': measid,
          'measurement': measurement,
          'user': users.get_current_user().email(),
          'logout_link': users.create_logout_url('/')
      }
      self.response.out.write(template.render(
          'templates/measurementdetail.html', template_args))