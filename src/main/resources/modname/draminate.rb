require 'json'
require 'open-uri'

class MissingData < StandardError; end

$parsed = {}
$fetcher = File
def set_file_fetcher(fetcher)
  $fetcher = fetcher
end

def read_array(name, version)
  $parsed["#{version}:#{name}"] ||= if version == current_version
                                      $fetcher.open("data/#{name}").read.split("\n")
                                    else
                                      open("https://raw.githubusercontent.com/4hrue2kd83f/MCDrama/#{version}/data/#{name}").read.split("\n")
                                    end
end

def current_version
  $current_version ||= begin
                         ENV.fetch('HEROKU_SLUG_COMMIT')
                       rescue
                         `git rev-parse HEAD`.strip
                       end[0..5]
end

def set_current_version(ver)
  $current_version = ver
end

def select_from_dict(dict, item, version)
  raise MissingData unless item
  hash = Hash.new { |h, k| h[k] = [] }
  read_array(dict, version).map { |x| x.split ":" }.each { |k,v| hash[k] << v }
  raise MissingData if hash[item].empty?
  hash[item].sample
end

def select_from_file(name, version, selections = {})
  read_array(name, version).sample
    .gsub(/\%([a-z]+)\%?/) do
    type = $1
    value = select_from_file type, version, selections
    selections[type] = value unless selections[type]
    value
  end
end

def draminate(version=current_version)
  begin
    selections = {}
    drama = select_from_file 'root', version, selections
    drama.gsub(/\$([a-z]+):([a-z]+)/) do
      source_type = $1
      attr = $2
      p source_type if source_type == 'mentioned'
      if attr == 'mentioned'
        raise MissingData unless selections[source_type]
        selections[source_type]
      else	
        select_from_dict(attr, selections[source_type], version)
      end
    end
  rescue MissingData => e
    retry
  end
end
